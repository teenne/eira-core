package org.eira.core.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler.
 * 
 * <p>The method must have exactly one parameter of a type implementing {@link EiraEvent}.
 * 
 * <pre>{@code
 * public class MyHandlers {
 *     
 *     @Subscribe
 *     public void onTeamCreated(TeamCreatedEvent event) {
 *         // Handle event
 *     }
 *     
 *     @Subscribe(priority = EventPriority.HIGH)
 *     public void onImportantEvent(ImportantEvent event) {
 *         // Runs before normal priority handlers
 *     }
 *     
 *     @Subscribe(async = true)
 *     public void onSlowEvent(SlowEvent event) {
 *         // Runs on async thread, won't block game
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {
    
    /**
     * The priority of this handler.
     * Higher priority handlers run first.
     * 
     * @return the priority level
     */
    EventPriority priority() default EventPriority.NORMAL;
    
    /**
     * Whether to run this handler asynchronously.
     * Async handlers run on a separate thread pool.
     * 
     * @return true for async execution
     */
    boolean async() default false;
    
    /**
     * Whether to receive cancelled events.
     * By default, cancelled events are not delivered.
     * 
     * @return true to receive cancelled events
     */
    boolean receiveCancelled() default false;
}
