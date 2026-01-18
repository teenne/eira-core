package org.eira.core.config;

import java.util.Optional;

/**
 * Shared configuration access for Eira mods.
 */
public interface EiraConfig {
    
    /**
     * Get a string value.
     */
    String getString(String key, String defaultValue);
    
    /**
     * Get an int value.
     */
    int getInt(String key, int defaultValue);
    
    /**
     * Get a boolean value.
     */
    boolean getBoolean(String key, boolean defaultValue);
    
    /**
     * Get a double value.
     */
    double getDouble(String key, double defaultValue);
    
    /**
     * Get a config section.
     */
    ConfigSection getSection(String path);
    
    /**
     * Register a config section for a mod.
     */
    void registerSection(String modId, java.util.function.Consumer<ConfigSection> defaults);
    
    /**
     * A subsection of configuration.
     */
    interface ConfigSection {
        String getString(String key, String defaultValue);
        int getInt(String key, int defaultValue);
        boolean getBoolean(String key, boolean defaultValue);
        double getDouble(String key, double defaultValue);
        void set(String key, Object value);
    }
}
