package org.eira.core.api.adventure;

import org.eira.core.api.team.Team;

import java.util.List;
import java.util.Optional;

/**
 * Manages adventure definitions and active instances.
 * 
 * <p>Adventures are timed challenges with checkpoints and leaderboards.
 * 
 * <p>Access via {@code EiraAPI.get().adventures()}.
 */
public interface AdventureManager {
    
    /**
     * Register an adventure.
     */
    void register(Adventure adventure);
    
    /**
     * Get an adventure by ID.
     */
    Optional<Adventure> get(String adventureId);
    
    /**
     * Get all registered adventures.
     */
    List<Adventure> getRegisteredAdventures();
    
    /**
     * Load adventures from config files.
     */
    void loadAdventures();
    
    /**
     * Start an adventure for a team.
     */
    AdventureInstance start(String adventureId, Team team);
    
    /**
     * Get active instance for a team.
     */
    Optional<AdventureInstance> getInstanceForTeam(Team team);
    
    /**
     * Get all active instances.
     */
    List<AdventureInstance> getActiveInstances();
    
    /**
     * Get leaderboard for an adventure.
     */
    Leaderboard getLeaderboard(String adventureId);
    
    /**
     * Shutdown and save all instances.
     */
    void shutdown();
}
