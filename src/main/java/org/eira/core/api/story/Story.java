package org.eira.core.api.story;

import java.util.List;

/**
 * Represents a story/narrative experience.
 */
public interface Story {
    
    String getId();
    
    String getName();
    
    List<Chapter> getChapters();
    
    Chapter getChapter(String chapterId);
    
    List<Secret> getSecrets();
    
    /**
     * A chapter in the story.
     */
    interface Chapter {
        String getId();
        String getName();
        List<String> getNpcIds();
        List<String> getRequirements();
        boolean isUnlockedBy(StoryState state);
    }
    
    /**
     * A secret that can be revealed.
     */
    interface Secret {
        String getId();
        int getMaxRevealLevel();
        List<String> getHintChapters();
        String getRevealChapter();
    }
}
