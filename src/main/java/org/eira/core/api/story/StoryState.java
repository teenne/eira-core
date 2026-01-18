package org.eira.core.api.story;

import java.util.List;

/**
 * Tracks a player or team's progress through a story.
 */
public interface StoryState {
    
    /**
     * Get the story ID.
     */
    String getStoryId();
    
    /**
     * Get the current chapter ID.
     */
    String getCurrentChapter();
    
    /**
     * Advance to a specific chapter.
     */
    void advanceToChapter(String chapterId);
    
    /**
     * Check if a chapter is unlocked.
     */
    boolean isChapterUnlocked(String chapterId);
    
    /**
     * Get all unlocked chapters.
     */
    List<String> getUnlockedChapters();
    
    /**
     * Set a story flag.
     */
    void setFlag(String flag, boolean value);
    
    /**
     * Check a story flag.
     */
    boolean hasFlag(String flag);
    
    /**
     * Mark a story event.
     */
    void markEvent(String eventId);
    
    /**
     * Check if an event has been marked.
     */
    boolean hasEvent(String eventId);
    
    /**
     * Get the reveal level for a secret (0 = not revealed).
     */
    int getSecretRevealLevel(String secretId);
    
    /**
     * Reveal a hint about a secret (increments reveal level).
     */
    void revealSecretHint(String secretId);
    
    /**
     * Get available dialogue topics with an NPC.
     */
    List<String> getAvailableDialogueTopics(String npcId);
    
    /**
     * Check if a topic can be discussed with an NPC.
     */
    boolean canDiscuss(String npcId, String topic);
}
