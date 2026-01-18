package org.eira.core.api.adventure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Builder for creating adventures with checkpoint chains.
 * 
 * <h2>Example: Escape Room Adventure</h2>
 * <pre>{@code
 * Adventure escapeRoom = AdventureBuilder.create("escape_room_1")
 *     .name("The Professor's Lab")
 *     .type(AdventureType.TIMED)
 *     .timeLimit(Duration.ofHours(1))
 *     .teamSize(2, 6)
 *     
 *     // Starting checkpoint - available immediately
 *     .checkpoint("start")
 *         .name("Enter the Lab")
 *         .triggeredByArea(-100, 64, -100, -80, 80, -80)
 *         .unlocks("find_guide", "explore")
 *         .onComplete(CheckpointAction.showTitle("Welcome", "The clock is ticking..."))
 *         .build()
 *     
 *     // Talk to guide NPC
 *     .checkpoint("find_guide")
 *         .name("Find the Guide")
 *         .triggeredByNPC("professor_hologram")
 *         .requires("start")
 *         .unlocks("first_puzzle")
 *         .hintFrom("professor_hologram", "Look for the holographic terminal")
 *         .points(10)
 *         .build()
 *     
 *     // First puzzle - can be solved by QR code OR redstone
 *     .checkpoint("first_puzzle")
 *         .name("Decode the Message")
 *         .trigger(CheckpointTrigger.any(
 *             CheckpointTrigger.http().onEndpoint("/puzzle/decode").build(),
 *             CheckpointTrigger.redstone().at(50, 65, 50).build()
 *         ))
 *         .requires("find_guide")
 *         .unlocks("get_key")
 *         .points(25)
 *         .build()
 *     
 *     // Get the key item
 *     .checkpoint("get_key")
 *         .name("Find the Key")
 *         .triggeredByItem("eira:lab_key")
 *         .requires("first_puzzle")
 *         .unlocks("escape")
 *         .points(25)
 *         .build()
 *     
 *     // Final escape
 *     .checkpoint("escape")
 *         .name("Escape!")
 *         .triggeredByArea(100, 64, 100, 120, 80, 120)
 *         .requires("get_key")
 *         .points(50)
 *         .onComplete(
 *             CheckpointAction.broadcast("Congratulations! You escaped!"),
 *             CheckpointAction.webhook("http://leaderboard/complete")
 *         )
 *         .build()
 *     
 *     // Optional bonus checkpoint
 *     .checkpoint("bonus_secret")
 *         .name("Find the Secret")
 *         .triggeredBySecret("professor_hologram", 2)
 *         .requires("find_guide")
 *         .optional()
 *         .hidden()
 *         .points(100)
 *         .build()
 *     
 *     .build();
 * }</pre>
 */
public class AdventureBuilder {
    
    private final String id;
    private String name;
    private Adventure.AdventureType type = Adventure.AdventureType.LINEAR;
    private Duration timeLimit;
    private int minTeamSize = 1;
    private int maxTeamSize = 10;
    private int maxTeams = 1;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private final Map<String, Object> metadata = new HashMap<>();
    
    private AdventureBuilder(String id) {
        this.id = id;
    }
    
    /**
     * Create a new adventure builder.
     */
    public static AdventureBuilder create(String id) {
        return new AdventureBuilder(id);
    }
    
    public AdventureBuilder name(String name) {
        this.name = name;
        return this;
    }
    
    public AdventureBuilder type(Adventure.AdventureType type) {
        this.type = type;
        return this;
    }
    
    public AdventureBuilder timeLimit(Duration timeLimit) {
        this.timeLimit = timeLimit;
        return this;
    }
    
    public AdventureBuilder teamSize(int min, int max) {
        this.minTeamSize = min;
        this.maxTeamSize = max;
        return this;
    }
    
    public AdventureBuilder maxTeams(int maxTeams) {
        this.maxTeams = maxTeams;
        return this;
    }
    
    /**
     * Add a checkpoint using builder.
     */
    public Checkpoint.Builder checkpoint(String id) {
        return new ChainedCheckpointBuilder(id, this);
    }
    
    /**
     * Add a pre-built checkpoint.
     */
    public AdventureBuilder addCheckpoint(Checkpoint checkpoint) {
        this.checkpoints.add(checkpoint);
        return this;
    }
    
    /**
     * Add multiple checkpoints.
     */
    public AdventureBuilder checkpoints(Checkpoint... checkpoints) {
        this.checkpoints.addAll(List.of(checkpoints));
        return this;
    }
    
    public AdventureBuilder metadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    /**
     * Build the adventure.
     */
    public Adventure build() {
        return new AdventureImpl(
            id, 
            name != null ? name : id, 
            type, 
            timeLimit, 
            minTeamSize, 
            maxTeamSize, 
            maxTeams, 
            List.copyOf(checkpoints),
            Map.copyOf(metadata)
        );
    }
    
    /**
     * Checkpoint builder that chains back to adventure builder.
     */
    private static class ChainedCheckpointBuilder extends Checkpoint.Builder {
        private final AdventureBuilder parent;
        
        ChainedCheckpointBuilder(String id, AdventureBuilder parent) {
            super(id);
            this.parent = parent;
        }
        
        @Override
        public Checkpoint build() {
            Checkpoint cp = super.build();
            parent.checkpoints.add(cp);
            return cp;
        }
        
        /**
         * Build checkpoint and return to adventure builder.
         */
        public AdventureBuilder done() {
            build();
            return parent;
        }
    }
}

/**
 * Adventure implementation.
 */
record AdventureImpl(
    String id,
    String name,
    Adventure.AdventureType type,
    Duration timeLimit,
    int minTeamSize,
    int maxTeamSize,
    int maxTeams,
    List<Checkpoint> checkpoints,
    Map<String, Object> metadata
) implements Adventure {
    
    @Override
    public String getId() { return id; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public AdventureType getType() { return type; }
    
    @Override
    public Duration getTimeLimit() { return timeLimit; }
    
    @Override
    public int getMinTeamSize() { return minTeamSize; }
    
    @Override
    public int getMaxTeamSize() { return maxTeamSize; }
    
    @Override
    public int getMaxTeams() { return maxTeams; }
    
    @Override
    public List<Checkpoint> getCheckpoints() { return checkpoints; }
    
    @Override
    public Checkpoint getCheckpoint(String checkpointId) {
        return checkpoints.stream()
            .filter(cp -> cp.getId().equals(checkpointId))
            .findFirst()
            .orElse(null);
    }
}
