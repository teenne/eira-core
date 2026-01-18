package org.eira.core.api.story;

import net.minecraft.world.entity.player.Player;
import org.eira.core.api.team.Team;

import java.util.List;
import java.util.Optional;

/**
 * Manages story definitions and progress tracking.
 * 
 * <p>Access via {@code EiraAPI.get().stories()}.
 */
public interface StoryManager {
    
    /**
     * Register a story.
     */
    void register(Story story);
    
    /**
     * Get a story by ID.
     */
    Optional<Story> get(String storyId);
    
    /**
     * Get all registered stories.
     */
    List<Story> getRegisteredStories();
    
    /**
     * Load stories from config files.
     */
    void loadStories();
    
    /**
     * Get player's state in a story.
     */
    StoryState getState(Player player, String storyId);
    
    /**
     * Get team's state in a story.
     */
    StoryState getState(Team team, String storyId);
    
    /**
     * Build context string for NPC system prompts.
     */
    String getContextForNPC(Player player, String npcCharacterId);
    
    /**
     * Handle a conversation event that might affect story state.
     */
    void handleConversationEvent(Player player, String npcId, String eventType);
}
