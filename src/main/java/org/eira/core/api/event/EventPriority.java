package org.eira.core.api.event;

/**
 * Priority levels for event handlers.
 * Higher priority handlers run before lower priority ones.
 */
public enum EventPriority {
    
    /**
     * Runs last. Use for monitoring/logging that shouldn't affect other handlers.
     */
    LOWEST(0),
    
    /**
     * Runs after normal handlers.
     */
    LOW(1),
    
    /**
     * Default priority for most handlers.
     */
    NORMAL(2),
    
    /**
     * Runs before normal handlers. Use when you need to modify event data.
     */
    HIGH(3),
    
    /**
     * Runs first. Use sparingly, typically for validation/cancellation.
     */
    HIGHEST(4),
    
    /**
     * Runs before all other handlers. Reserved for system-level handling.
     */
    MONITOR(5);
    
    private final int value;
    
    EventPriority(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
}
