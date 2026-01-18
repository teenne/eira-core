package org.eira.core.api.player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Key-value progress data storage for players.
 * 
 * <p>Similar to {@link org.eira.core.api.team.TeamData} but for individual players.
 */
public interface PlayerProgress {
    
    void set(String key, Object value);
    
    Optional<Object> get(String key);
    
    Object get(String key, Object defaultValue);
    
    boolean has(String key);
    
    void remove(String key);
    
    void clear();
    
    Set<String> getKeys();
    
    Map<String, Object> getAll();
    
    // Typed getters
    String getString(String key, String defaultValue);
    int getInt(String key, int defaultValue);
    long getLong(String key, long defaultValue);
    double getDouble(String key, double defaultValue);
    boolean getBoolean(String key, boolean defaultValue);
    
    // Flag operations
    void setFlag(String key, boolean value);
    boolean getFlag(String key);
    
    // Atomic operations
    long increment(String key, long delta);
    default long decrement(String key, long delta) {
        return increment(key, -delta);
    }
}
