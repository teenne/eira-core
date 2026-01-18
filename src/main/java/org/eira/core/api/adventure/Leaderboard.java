package org.eira.core.api.adventure;

import org.eira.core.api.team.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Leaderboard for adventure completions.
 */
public interface Leaderboard {
    
    /**
     * Get the adventure ID this leaderboard is for.
     */
    String getAdventureId();
    
    /**
     * Get top N entries.
     */
    List<LeaderboardEntry> getTop(int count);
    
    /**
     * Get a team's rank (1-indexed).
     */
    Optional<Integer> getRank(Team team);
    
    /**
     * Get a team's entry.
     */
    Optional<LeaderboardEntry> getEntry(Team team);
    
    /**
     * Get entries after a certain time.
     */
    List<LeaderboardEntry> getEntriesAfter(Instant after);
    
    /**
     * Get total entry count.
     */
    int getEntryCount();
    
    /**
     * A single leaderboard entry.
     */
    interface LeaderboardEntry {
        
        /**
         * Get the team name.
         */
        String getTeamName();
        
        /**
         * Get completion time.
         */
        Duration getCompletionTime();
        
        /**
         * Get when completed.
         */
        Instant getCompletedAt();
        
        /**
         * Get checkpoints completed.
         */
        int getCheckpointsCompleted();
        
        /**
         * Get total checkpoints.
         */
        int getTotalCheckpoints();
    }
}
