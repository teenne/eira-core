package org.eira.core.api.player;

import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Manages extended player data and progress tracking.
 * 
 * <p>Access via {@code EiraAPI.get().players()}.
 */
public interface PlayerManager {
    
    /**
     * Get extended player data.
     */
    EiraPlayer get(Player player);
    
    /**
     * Get extended player data by UUID.
     */
    Optional<EiraPlayer> get(UUID playerId);
    
    /**
     * Check if player data exists.
     */
    boolean has(UUID playerId);
}
