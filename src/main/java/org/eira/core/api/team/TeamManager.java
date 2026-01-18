package org.eira.core.api.team;

import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages teams of players.
 * 
 * <p>Access via {@code EiraAPI.get().teams()}.
 * 
 * <h2>Creating Teams</h2>
 * <pre>{@code
 * Team team = teams.create("Red Dragons")
 *     .withColor(ChatFormatting.RED)
 *     .withMaxSize(4)
 *     .withTag("escape_room")
 *     .build();
 * }</pre>
 * 
 * <h2>Finding Teams</h2>
 * <pre>{@code
 * Optional<Team> team = teams.getById(teamId);
 * Optional<Team> playerTeam = teams.getTeamOf(player);
 * List<Team> escapeTeams = teams.getByTag("escape_room");
 * }</pre>
 */
public interface TeamManager {
    
    /**
     * Create a new team builder.
     * 
     * @param name the team name
     * @return a builder for configuring the team
     */
    TeamBuilder create(String name);
    
    /**
     * Get a team by its ID.
     */
    Optional<Team> getById(UUID id);
    
    /**
     * Get a team by its name (case-insensitive).
     */
    Optional<Team> getByName(String name);
    
    /**
     * Get all teams.
     */
    List<Team> getAll();
    
    /**
     * Get all teams with a specific tag.
     */
    List<Team> getByTag(String tag);
    
    /**
     * Get the team a player belongs to.
     */
    Optional<Team> getTeamOf(Player player);
    
    /**
     * Get the team a player belongs to by UUID.
     */
    Optional<Team> getTeamOf(UUID playerId);
    
    /**
     * Check if a player is on any team.
     */
    boolean hasTeam(Player player);
    
    /**
     * Remove a team by ID.
     */
    boolean remove(UUID teamId);
    
    /**
     * Get total number of teams.
     */
    int getTeamCount();
    
    /**
     * Builder for creating teams.
     */
    interface TeamBuilder {
        
        /**
         * Set the team color.
         */
        TeamBuilder withColor(ChatFormatting color);
        
        /**
         * Set the maximum team size.
         */
        TeamBuilder withMaxSize(int maxSize);
        
        /**
         * Set a tag for filtering/grouping.
         */
        TeamBuilder withTag(String tag);
        
        /**
         * Add initial custom data.
         */
        TeamBuilder withData(String key, Object value);
        
        /**
         * Set the team leader.
         */
        TeamBuilder withLeader(Player leader);
        
        /**
         * Add initial members.
         */
        TeamBuilder withMembers(Player... members);
        
        /**
         * Build and register the team.
         */
        Team build();
    }
}
