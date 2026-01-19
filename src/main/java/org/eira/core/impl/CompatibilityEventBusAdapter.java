package org.eira.core.impl;

import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.events.EiraEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Adapter that provides the compatibility event bus interface (org.eira.core.api.events.EiraEventBus)
 * by wrapping the rich event bus implementation.
 *
 * <p>This allows Eira Relay and other mods using the compatibility API to work with
 * the full-featured event bus implementation.
 */
public class CompatibilityEventBusAdapter implements org.eira.core.api.events.EiraEventBus {

    private final EiraEventBus delegate;
    private final Map<ConsumerKey<?>, EiraEventBus.Subscription> subscriptions = new ConcurrentHashMap<>();

    public CompatibilityEventBusAdapter(EiraEventBus delegate) {
        this.delegate = delegate;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EiraEvent> void subscribe(Class<T> eventType, Consumer<T> handler) {
        // Since EiraEvent (compat) extends EiraEvent (rich), we can safely cast
        Class<org.eira.core.api.event.EiraEvent> richEventType =
            (Class<org.eira.core.api.event.EiraEvent>) (Class<?>) eventType;
        Consumer<org.eira.core.api.event.EiraEvent> richHandler =
            (Consumer<org.eira.core.api.event.EiraEvent>) (Consumer<?>) handler;

        EiraEventBus.Subscription subscription = delegate.subscribe(richEventType, richHandler);
        subscriptions.put(new ConsumerKey<>(eventType, handler), subscription);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends EiraEvent> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        ConsumerKey<?> key = new ConsumerKey<>(eventType, handler);
        EiraEventBus.Subscription subscription = subscriptions.remove(key);
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void publish(EiraEvent event) {
        // EiraEvent (compat) extends EiraEvent (rich), so this cast is safe
        delegate.publish((org.eira.core.api.event.EiraEvent) event);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasSubscribers(Class<? extends EiraEvent> eventType) {
        return delegate.hasSubscribers((Class<? extends org.eira.core.api.event.EiraEvent>) eventType);
    }

    private record ConsumerKey<T>(Class<T> eventClass, Consumer<T> handler) {}
}
