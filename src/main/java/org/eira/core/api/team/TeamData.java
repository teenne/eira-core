package org.eira.core.api.team;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Key-value data storage for teams.
 * 
 * <p>Stores arbitrary data that persists with the team.
 * Thread-safe for concurrent access.
 * 
 * <pre>{@code
 * TeamData data = team.getData();
 * 
 * // Store values
 * data.set("score", 150);
 * data.set("current_room", "library");
 * 
 * // Retrieve values
 * int score = data.getInt("score", 0);
 * String room = data.getString("current_room", "entrance");
 * 
 * // Atomic operations
 * data.increment("score", 50);
 * data.addToList("solved_puzzles", "puzzle_5");
 * }</pre>
 */
public interface TeamData {
    
    // ==================== Basic Operations ====================
    
    /**
     * Set a value.
     */
    void set(String key, Object value);
    
    /**
     * Get a value.
     */
    Optional<Object> get(String key);
    
    /**
     * Get a value with default.
     */
    Object get(String key, Object defaultValue);
    
    /**
     * Check if a key exists.
     */
    boolean has(String key);
    
    /**
     * Remove a value.
     */
    void remove(String key);
    
    /**
     * Clear all data.
     */
    void clear();
    
    /**
     * Get all keys.
     */
    Set<String> getKeys();
    
    /**
     * Get all data as a map.
     */
    Map<String, Object> getAll();
    
    // ==================== Typed Getters ====================
    
    /**
     * Get a string value.
     */
    String getString(String key, String defaultValue);
    
    /**
     * Get an integer value.
     */
    int getInt(String key, int defaultValue);
    
    /**
     * Get a long value.
     */
    long getLong(String key, long defaultValue);
    
    /**
     * Get a double value.
     */
    double getDouble(String key, double defaultValue);
    
    /**
     * Get a boolean value.
     */
    boolean getBoolean(String key, boolean defaultValue);
    
    /**
     * Get a list value.
     */
    <T> List<T> getList(String key, Class<T> elementType);
    
    // ==================== Atomic Operations ====================
    
    /**
     * Atomically increment a numeric value.
     * 
     * @return the new value
     */
    long increment(String key, long delta);
    
    /**
     * Atomically decrement a numeric value.
     * 
     * @return the new value
     */
    default long decrement(String key, long delta) {
        return increment(key, -delta);
    }
    
    /**
     * Atomically add an item to a list.
     */
    <T> void addToList(String key, T item);
    
    /**
     * Atomically remove an item from a list.
     */
    <T> boolean removeFromList(String key, T item);
    
    /**
     * Atomically set if not exists.
     * 
     * @return true if value was set, false if key already existed
     */
    boolean setIfAbsent(String key, Object value);
}
