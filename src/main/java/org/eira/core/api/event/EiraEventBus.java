package org.eira.core.api.event;

import java.util.function.Consumer;

/**
 * Cross-mod event bus for the Eira ecosystem.
 * 
 * <p>Allows mods to publish events and subscribe to events from other mods
 * without direct dependencies.
 * 
 * <h2>Publishing Events</h2>
 * <pre>{@code
 * // Fire and forget
 * events.publish(new MyEvent(data));
 * 
 * // Synchronous (waits for handlers)
 * events.publishSync(new ImportantEvent(data));
 * }</pre>
 * 
 * <h2>Subscribing to Events</h2>
 * <pre>{@code
 * // Lambda subscription
 * events.subscribe(TeamCreatedEvent.class, event -> {
 *     logger.info("Team created: " + event.team().getName());
 * });
 * 
 * // With priority
 * events.subscribe(MyEvent.class, EventPriority.HIGH, event -> { ... });
 * 
 * // Annotation-based handler class
 * events.registerHandler(new MyEventHandlers());
 * }</pre>
 */
public interface EiraEventBus {
    
    /**
     * Publish an event asynchronously.
     * Returns immediately; handlers run on event thread.
     * 
     * @param event the event to publish
     * @param <T> the event type
     */
    <T extends EiraEvent> void publish(T event);
    
    /**
     * Publish an event synchronously.
     * Blocks until all handlers complete.
     * 
     * @param event the event to publish
     * @param <T> the event type
     */
    <T extends EiraEvent> void publishSync(T event);
    
    /**
     * Publish an event with completion callback.
     * 
     * @param event the event to publish
     * @param callback called after all handlers complete
     * @param <T> the event type
     */
    <T extends EiraEvent> void publish(T event, Consumer<T> callback);
    
    /**
     * Subscribe to an event type.
     * 
     * @param eventClass the event class to subscribe to
     * @param handler the event handler
     * @param <T> the event type
     * @return subscription that can be used to unsubscribe
     */
    <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, Consumer<T> handler);
    
    /**
     * Subscribe to an event type with priority.
     * 
     * @param eventClass the event class to subscribe to
     * @param priority handler priority (higher runs first)
     * @param handler the event handler
     * @param <T> the event type
     * @return subscription that can be used to unsubscribe
     */
    <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, EventPriority priority, Consumer<T> handler);
    
    /**
     * Subscribe to an event type with options.
     * 
     * @param eventClass the event class to subscribe to
     * @param options subscription options
     * @param handler the event handler
     * @param <T> the event type
     * @return subscription that can be used to unsubscribe
     */
    <T extends EiraEvent> Subscription subscribe(Class<T> eventClass, SubscriptionOptions options, Consumer<T> handler);
    
    /**
     * Register an annotated event handler class.
     * Methods annotated with {@link Subscribe} will be registered.
     * 
     * @param handler the handler instance
     */
    void registerHandler(Object handler);
    
    /**
     * Unregister a previously registered handler class.
     * 
     * @param handler the handler instance to unregister
     */
    void unregisterHandler(Object handler);
    
    /**
     * Unsubscribe using a subscription.
     * 
     * @param subscription the subscription to cancel
     */
    void unsubscribe(Subscription subscription);
    
    /**
     * Check if there are any subscribers for an event type.
     * 
     * @param eventClass the event class
     * @return true if there are subscribers
     */
    boolean hasSubscribers(Class<? extends EiraEvent> eventClass);
    
    /**
     * Represents a subscription that can be cancelled.
     */
    interface Subscription {
        /**
         * Cancel this subscription.
         */
        void cancel();
        
        /**
         * Check if this subscription is still active.
         */
        boolean isActive();
    }
    
    /**
     * Options for subscriptions.
     */
    record SubscriptionOptions(
        EventPriority priority,
        boolean async,
        boolean receiveCancelled
    ) {
        public static SubscriptionOptions defaults() {
            return new SubscriptionOptions(EventPriority.NORMAL, false, false);
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private EventPriority priority = EventPriority.NORMAL;
            private boolean async = false;
            private boolean receiveCancelled = false;
            
            public Builder priority(EventPriority priority) {
                this.priority = priority;
                return this;
            }
            
            public Builder async(boolean async) {
                this.async = async;
                return this;
            }
            
            public Builder receiveCancelled(boolean receive) {
                this.receiveCancelled = receive;
                return this;
            }
            
            public SubscriptionOptions build() {
                return new SubscriptionOptions(priority, async, receiveCancelled);
            }
        }
    }
}
