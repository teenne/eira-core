package org.eira.core.api;

import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.team.TeamManager;
import org.eira.core.api.player.PlayerManager;
import org.eira.core.api.story.StoryManager;
import org.eira.core.api.adventure.AdventureManager;
import org.eira.core.config.EiraConfig;
import org.eira.core.network.EiraNetwork;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Main entry point for the Eira Core API.
 * 
 * <p>Provides access to all Eira subsystems:
 * <ul>
 *   <li>{@link #events()} - Cross-mod event bus</li>
 *   <li>{@link #teams()} - Team management</li>
 *   <li>{@link #players()} - Extended player data</li>
 *   <li>{@link #stories()} - Story/narrative framework</li>
 *   <li>{@link #adventures()} - Adventure/objective system</li>
 *   <li>{@link #config()} - Shared configuration</li>
 *   <li>{@link #network()} - Cross-mod networking</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>{@code
 * // Get API instance
 * EiraAPI eira = EiraAPI.get();
 * 
 * // Use subsystems
 * eira.events().subscribe(TeamCreatedEvent.class, event -> { ... });
 * Team team = eira.teams().create("My Team").build();
 * }</pre>
 * 
 * <h2>Soft Dependency Usage</h2>
 * <pre>{@code
 * // Safe access when Eira Core might not be present
 * EiraAPI.ifPresent(eira -> {
 *     // Use API safely
 * });
 * }</pre>
 * 
 * @since 1.0.0
 */
public interface EiraAPI {
    
    /**
     * Get the Eira API instance.
     * 
     * @return the API instance
     * @throws IllegalStateException if Eira Core is not loaded
     */
    static EiraAPI get() {
        return EiraAPIProvider.get();
    }
    
    /**
     * Get the Eira API if available.
     * 
     * @return Optional containing the API, or empty if not loaded
     */
    static Optional<EiraAPI> getOptional() {
        return EiraAPIProvider.getOptional();
    }
    
    /**
     * Execute action if Eira API is available.
     * Safe for soft dependencies.
     * 
     * @param action the action to execute with the API
     */
    static void ifPresent(Consumer<EiraAPI> action) {
        getOptional().ifPresent(action);
    }
    
    /**
     * Check if Eira Core is loaded and available.
     * 
     * @return true if API is available
     */
    static boolean isAvailable() {
        return EiraAPIProvider.isAvailable();
    }
    
    // ==================== Subsystems ====================
    
    /**
     * Get the cross-mod event bus.
     * 
     * <p>Use to publish events and subscribe to events from any Eira mod.
     * 
     * @return the event bus
     */
    EiraEventBus events();
    
    /**
     * Get the team manager.
     * 
     * <p>Create and manage teams of players for cooperative gameplay.
     * 
     * @return the team manager
     */
    TeamManager teams();
    
    /**
     * Get the player manager.
     * 
     * <p>Access extended player data and progress tracking.
     * 
     * @return the player manager
     */
    PlayerManager players();
    
    /**
     * Get the story manager.
     * 
     * <p>Define and track narrative experiences with chapters and secrets.
     * 
     * @return the story manager
     */
    StoryManager stories();
    
    /**
     * Get the adventure manager.
     * 
     * <p>Run timed adventures with checkpoints and leaderboards.
     * 
     * @return the adventure manager
     */
    AdventureManager adventures();
    
    /**
     * Get the shared configuration.
     * 
     * @return the configuration accessor
     */
    EiraConfig config();
    
    /**
     * Get the network API.
     * 
     * <p>Register and send cross-mod packets.
     * 
     * @return the network API
     */
    EiraNetwork network();
}
