package org.eira.core.api.player;

import net.minecraft.world.entity.player.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Extended player data wrapper.
 * 
 * <p>Provides access to progress tracking, session data, and activity history.
 */
public interface EiraPlayer {
    
    /**
     * Get the player UUID.
     */
    UUID getUUID();
    
    /**
     * Get the player's name.
     */
    String getName();
    
    /**
     * Get the underlying Minecraft player if online.
     */
    Optional<Player> getPlayer();
    
    /**
     * Check if the player is online.
     */
    boolean isOnline();
    
    /**
     * Get the player's progress data (persists across sessions).
     */
    PlayerProgress getProgress();
    
    /**
     * Get session-only data (cleared on logout).
     */
    PlayerProgress getSessionData();
    
    /**
     * Get persistent data (saved to world).
     */
    PlayerProgress getPersistentData();
    
    /**
     * Get time in current session.
     */
    Duration getSessionTime();
    
    /**
     * Get last active timestamp.
     */
    Instant getLastActiveTime();
    
    /**
     * Record an activity.
     */
    void recordActivity(String action, java.util.Map<String, Object> data);
}
