package org.eira.core.api.event;

/**
 * Base interface for all Eira events.
 * 
 * <p>Events are typically implemented as records for immutability:
 * <pre>{@code
 * public record MyCustomEvent(
 *     Player player,
 *     String action,
 *     Map<String, Object> data
 * ) implements EiraEvent { }
 * }</pre>
 * 
 * <p>For cancellable events, implement {@link Cancellable}:
 * <pre>{@code
 * public class MyCancellableEvent implements EiraEvent, Cancellable {
 *     private boolean cancelled;
 *     private final String data;
 *     
 *     public MyCancellableEvent(String data) {
 *         this.data = data;
 *     }
 *     
 *     public String getData() { return data; }
 *     
 *     @Override
 *     public boolean isCancelled() { return cancelled; }
 *     
 *     @Override
 *     public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
 * }
 * }</pre>
 */
public interface EiraEvent {
    
    /**
     * Interface for events that can be cancelled by handlers.
     */
    interface Cancellable {
        /**
         * Check if this event has been cancelled.
         * 
         * @return true if cancelled
         */
        boolean isCancelled();
        
        /**
         * Set the cancelled state.
         * 
         * @param cancelled true to cancel the event
         */
        void setCancelled(boolean cancelled);
    }
}
