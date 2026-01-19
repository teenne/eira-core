package org.eira.core.impl;

import org.eira.core.EiraCore;
import org.eira.core.api.event.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Implementation of the Eira event bus for local mod communication.
 *
 * <p>Provides publish/subscribe functionality with:
 * <ul>
 *   <li>Priority-based handler ordering</li>
 *   <li>Async event publishing</li>
 *   <li>Cancellable event support</li>
 *   <li>Annotation-based handler registration</li>
 * </ul>
 */
public class EiraEventBusImpl implements EiraEventBus {

    // Map of event class -> list of handlers sorted by priority
    private final Map<Class<?>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();

    // Map of handler object -> list of subscriptions for that object
    private final Map<Object, List<Subscription>> handlerSubscriptions = new ConcurrentHashMap<>();

    // Executor for async events
    private final ExecutorService asyncExecutor;

    // Subscription ID counter
    private final AtomicLong subscriptionIdCounter = new AtomicLong(0);

    public EiraEventBusImpl() {
        this.asyncExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "eira-event-bus");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Publishing ====================

    @Override
    public <T extends EiraEvent> void publish(T event) {
        asyncExecutor.execute(() -> deliverEvent(event));
    }

    @Override
    public <T extends EiraEvent> void publishSync(T event) {
        deliverEvent(event);
    }

    @Override
    public <T extends EiraEvent> void publish(T event, Consumer<T> callback) {
        asyncExecutor.execute(() -> {
            deliverEvent(event);
            if (callback != null) {
                callback.accept(event);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends EiraEvent> void deliverEvent(T event) {
        Class<?> eventClass = event.getClass();
        List<HandlerEntry<?>> eventHandlers = handlers.get(eventClass);

        if (eventHandlers == null || eventHandlers.isEmpty()) {
            return;
        }

        boolean isCancellable = event instanceof EiraEvent.Cancellable;

        for (HandlerEntry<?> entry : eventHandlers) {
            // Skip cancelled events unless handler opts in
            if (isCancellable && ((EiraEvent.Cancellable) event).isCancelled()) {
                if (!entry.options.receiveCancelled()) {
                    continue;
                }
            }

            try {
                if (entry.options.async()) {
                    asyncExecutor.execute(() -> invokeHandler(entry, event));
                } else {
                    invokeHandler(entry, event);
                }
            } catch (Exception e) {
                EiraCore.LOGGER.error("Error in event handler for {}: {}",
                    eventClass.getSimpleName(), e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends EiraEvent> void invokeHandler(HandlerEntry<?> entry, T event) {
        try {
            ((Consumer<T>) entry.handler).accept(event);
        } catch (Exception e) {
            EiraCore.LOGGER.error("Exception in event handler: {}", e.getMessage(), e);
        }
    }

    // ==================== Subscribing ====================

    @Override
    public <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, Consumer<T> handler) {
        return subscribe(eventClass, SubscriptionOptions.defaults(), handler);
    }

    @Override
    public <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, EventPriority priority, Consumer<T> handler) {
        SubscriptionOptions options = SubscriptionOptions.builder()
            .priority(priority)
            .build();
        return subscribe(eventClass, options, handler);
    }

    @Override
    public <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, SubscriptionOptions options, Consumer<T> handler) {
        long id = subscriptionIdCounter.incrementAndGet();
        SubscriptionImpl subscription = new SubscriptionImpl(id, eventClass);

        HandlerEntry<T> entry = new HandlerEntry<>(id, handler, options, subscription);

        handlers.compute(eventClass, (key, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(entry);
            // Sort by priority (highest first)
            list.sort((a, b) -> Integer.compare(
                b.options.priority().getValue(),
                a.options.priority().getValue()
            ));
            return list;
        });

        EiraCore.LOGGER.debug("Subscribed to {} with priority {} (id={})",
            eventClass.getSimpleName(), options.priority(), id);

        return subscription;
    }

    @Override
    public void registerHandler(Object handler) {
        Class<?> handlerClass = handler.getClass();
        List<Subscription> subscriptions = new ArrayList<>();

        for (Method method : handlerClass.getDeclaredMethods()) {
            Subscribe annotation = method.getAnnotation(Subscribe.class);
            if (annotation == null) {
                continue;
            }

            // Validate method signature
            if (method.getParameterCount() != 1) {
                EiraCore.LOGGER.warn("@Subscribe method {} must have exactly one parameter",
                    method.getName());
                continue;
            }

            Class<?> paramType = method.getParameterTypes()[0];
            if (!EiraEvent.class.isAssignableFrom(paramType)) {
                EiraCore.LOGGER.warn("@Subscribe method {} parameter must implement EiraEvent",
                    method.getName());
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends EiraEvent> eventClass = (Class<? extends EiraEvent>) paramType;

            SubscriptionOptions options = SubscriptionOptions.builder()
                .priority(annotation.priority())
                .async(annotation.async())
                .receiveCancelled(annotation.receiveCancelled())
                .build();

            method.setAccessible(true);

            Consumer<EiraEvent> consumer = event -> {
                try {
                    method.invoke(handler, event);
                } catch (Exception e) {
                    EiraCore.LOGGER.error("Error invoking @Subscribe method {}: {}",
                        method.getName(), e.getMessage(), e);
                }
            };

            Subscription subscription = subscribeRaw(eventClass, options, consumer);
            subscriptions.add(subscription);
        }

        if (!subscriptions.isEmpty()) {
            handlerSubscriptions.put(handler, subscriptions);
            EiraCore.LOGGER.debug("Registered handler {} with {} subscriptions",
                handlerClass.getSimpleName(), subscriptions.size());
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends EiraEvent> Subscription subscribeRaw(
            Class<T> eventClass, SubscriptionOptions options, Consumer<EiraEvent> handler) {
        return subscribe(eventClass, options, (Consumer<T>) handler);
    }

    @Override
    public void unregisterHandler(Object handler) {
        List<Subscription> subscriptions = handlerSubscriptions.remove(handler);
        if (subscriptions != null) {
            for (Subscription subscription : subscriptions) {
                subscription.cancel();
            }
            EiraCore.LOGGER.debug("Unregistered handler {} with {} subscriptions",
                handler.getClass().getSimpleName(), subscriptions.size());
        }
    }

    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription instanceof SubscriptionImpl impl) {
            impl.cancel();
        }
    }

    @Override
    public boolean hasSubscribers(Class<? extends EiraEvent> eventClass) {
        List<HandlerEntry<?>> eventHandlers = handlers.get(eventClass);
        return eventHandlers != null && !eventHandlers.isEmpty();
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the event bus and release resources.
     */
    public void shutdown() {
        asyncExecutor.shutdown();
        handlers.clear();
        handlerSubscriptions.clear();
    }

    /**
     * Get statistics about the event bus.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("eventTypes", handlers.size());
        stats.put("totalSubscriptions", handlers.values().stream().mapToInt(List::size).sum());
        stats.put("registeredHandlers", handlerSubscriptions.size());
        return stats;
    }

    // ==================== Inner Classes ====================

    /**
     * Entry storing handler and options.
     */
    private record HandlerEntry<T extends EiraEvent>(
        long id,
        Consumer<T> handler,
        SubscriptionOptions options,
        SubscriptionImpl subscription
    ) {}

    /**
     * Subscription implementation that can cancel itself.
     */
    private class SubscriptionImpl implements Subscription {
        private final long id;
        private final Class<?> eventClass;
        private volatile boolean active = true;

        SubscriptionImpl(long id, Class<?> eventClass) {
            this.id = id;
            this.eventClass = eventClass;
        }

        @Override
        public void cancel() {
            if (!active) {
                return;
            }
            active = false;

            handlers.computeIfPresent(eventClass, (key, list) -> {
                list.removeIf(entry -> entry.id == id);
                return list.isEmpty() ? null : list;
            });
        }

        @Override
        public boolean isActive() {
            return active;
        }
    }
}
