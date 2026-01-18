package org.eira.core.api;

import java.util.Optional;

/**
 * Internal provider for the Eira API instance.
 * Handles registration and safe access.
 */
public final class EiraAPIProvider {
    
    private static EiraAPI instance;
    
    private EiraAPIProvider() {
        // Prevent instantiation
    }
    
    /**
     * Register the API instance. Called by EiraCore during initialization.
     * 
     * @param api the API instance
     * @throws IllegalStateException if already registered
     */
    public static void register(EiraAPI api) {
        if (instance != null) {
            throw new IllegalStateException("EiraAPI already registered");
        }
        instance = api;
    }
    
    /**
     * Get the API instance.
     * 
     * @return the API instance
     * @throws IllegalStateException if not yet registered
     */
    public static EiraAPI get() {
        if (instance == null) {
            throw new IllegalStateException(
                "EiraAPI not available. Make sure eira-core is loaded and initialized."
            );
        }
        return instance;
    }
    
    /**
     * Get the API instance if available.
     * 
     * @return Optional containing the API, or empty if not available
     */
    public static Optional<EiraAPI> getOptional() {
        return Optional.ofNullable(instance);
    }
    
    /**
     * Check if the API is available.
     * 
     * @return true if API is registered and available
     */
    public static boolean isAvailable() {
        return instance != null;
    }
    
    /**
     * Unregister the API. Called during shutdown.
     */
    public static void unregister() {
        instance = null;
    }
}
