package org.eira.core.api.adventure;

import org.eira.core.api.team.Team;

import java.time.Duration;
import java.util.List;

/**
 * A running instance of an adventure for a specific team.
 */
public interface AdventureInstance {
    
    /**
     * Get the adventure definition.
     */
    Adventure getAdventure();
    
    /**
     * Get the team running this adventure.
     */
    Team getTeam();
    
    /**
     * Get current state.
     */
    AdventureState getState();
    
    /**
     * Get progress (0.0 - 1.0).
     */
    float getProgress();
    
    /**
     * Get elapsed time.
     */
    Duration getElapsedTime();
    
    /**
     * Get remaining time (for timed adventures).
     */
    Duration getRemainingTime();
    
    /**
     * Get current checkpoint.
     */
    Checkpoint getCurrentCheckpoint();
    
    /**
     * Get completed checkpoints.
     */
    List<Checkpoint> getCompletedCheckpoints();
    
    /**
     * Get remaining checkpoints.
     */
    List<Checkpoint> getRemainingCheckpoints();
    
    /**
     * Manually complete a checkpoint.
     */
    void completeCheckpoint(String checkpointId);
    
    /**
     * Skip a checkpoint.
     */
    void skipCheckpoint(String checkpointId);
    
    /**
     * Add bonus time.
     */
    void addTime(Duration bonus);
    
    /**
     * Mark adventure as completed.
     */
    void complete();
    
    /**
     * Mark adventure as failed.
     */
    void fail(String reason);
    
    /**
     * Reset the adventure.
     */
    void reset();
    
    enum AdventureState {
        NOT_STARTED,
        RUNNING,
        PAUSED,
        COMPLETED,
        FAILED,
        ABANDONED
    }
}
