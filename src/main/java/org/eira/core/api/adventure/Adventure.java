package org.eira.core.api.adventure;

import java.time.Duration;
import java.util.List;

/**
 * Defines an adventure with checkpoints and objectives.
 */
public interface Adventure {
    
    String getId();
    
    String getName();
    
    AdventureType getType();
    
    Duration getTimeLimit();
    
    int getMinTeamSize();
    
    int getMaxTeamSize();
    
    int getMaxTeams();
    
    List<Checkpoint> getCheckpoints();
    
    Checkpoint getCheckpoint(String checkpointId);
    
    enum AdventureType {
        LINEAR,      // Sequential checkpoints
        OPEN,        // Any order
        TIMED,       // Time limit
        COMPETITIVE, // Team vs team
        COOPERATIVE  // Team together
    }
}
