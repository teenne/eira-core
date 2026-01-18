package org.eira.core.api.adventure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A checkpoint/objective in an adventure.
 * 
 * <p>Checkpoints can be triggered by various events and can unlock other checkpoints.
 * This enables creating complex adventure flows:
 * 
 * <pre>
 * [Start] ──► [Talk to Guide] ──► [Find Key] ──┬──► [Open Door] ──► [Escape]
 *                                               │
 *                     [Scan QR Code] ──────────┘
 * </pre>
 * 
 * <h2>Checkpoint States</h2>
 * <ul>
 *   <li><b>LOCKED</b> - Prerequisites not met</li>
 *   <li><b>AVAILABLE</b> - Can be completed</li>
 *   <li><b>IN_PROGRESS</b> - Partially completed (for composite triggers)</li>
 *   <li><b>COMPLETED</b> - Successfully finished</li>
 *   <li><b>SKIPPED</b> - Manually skipped</li>
 *   <li><b>FAILED</b> - Failed (for timed checkpoints)</li>
 * </ul>
 */
public interface Checkpoint {
    
    /**
     * Get checkpoint ID (unique within adventure).
     */
    String getId();
    
    /**
     * Get display name.
     */
    String getName();
    
    /**
     * Get description/hint text.
     */
    String getDescription();
    
    /**
     * Get the trigger that completes this checkpoint.
     */
    CheckpointTrigger getTrigger();
    
    /**
     * Get IDs of checkpoints that must be completed before this one is available.
     * Empty list means this checkpoint is available from the start.
     */
    List<String> getPrerequisites();
    
    /**
     * Get IDs of checkpoints that this checkpoint unlocks when completed.
     */
    List<String> getUnlocks();
    
    /**
     * Get optional NPC that gives hints for this checkpoint.
     */
    Optional<String> getHintNpcId();
    
    /**
     * Get optional hint message.
     */
    Optional<String> getHintMessage();
    
    /**
     * Check if this checkpoint is optional for adventure completion.
     */
    boolean isOptional();
    
    /**
     * Check if this checkpoint is hidden until prerequisites are met.
     */
    boolean isHidden();
    
    /**
     * Get time limit for this checkpoint (null if no limit).
     */
    Optional<java.time.Duration> getTimeLimit();
    
    /**
     * Get points/score awarded for completing this checkpoint.
     */
    int getPoints();
    
    /**
     * Get actions to execute when checkpoint is completed.
     */
    List<CheckpointAction> getOnCompleteActions();
    
    /**
     * Get custom metadata.
     */
    Map<String, Object> getMetadata();
    
    // ==================== State Tracking ====================
    
    /**
     * Checkpoint state in an adventure instance.
     */
    enum State {
        /** Prerequisites not yet met */
        LOCKED,
        /** Available to be completed */
        AVAILABLE,
        /** Partially completed (composite triggers) */
        IN_PROGRESS,
        /** Successfully completed */
        COMPLETED,
        /** Manually skipped */
        SKIPPED,
        /** Failed (timed out or conditions failed) */
        FAILED
    }
    
    // ==================== Builder ====================
    
    /**
     * Create a new checkpoint builder.
     */
    static Builder builder(String id) {
        return new Builder(id);
    }
    
    class Builder {
        private final String id;
        private String name;
        private String description;
        private CheckpointTrigger trigger;
        private List<String> prerequisites = List.of();
        private List<String> unlocks = List.of();
        private String hintNpcId;
        private String hintMessage;
        private boolean optional = false;
        private boolean hidden = false;
        private java.time.Duration timeLimit;
        private int points = 0;
        private List<CheckpointAction> onCompleteActions = List.of();
        private Map<String, Object> metadata = Map.of();
        
        public Builder(String id) {
            this.id = id;
            this.name = id;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder trigger(CheckpointTrigger trigger) {
            this.trigger = trigger;
            return this;
        }
        
        // Convenience trigger methods
        public Builder triggeredByNPC(String npcId) {
            this.trigger = CheckpointTrigger.npc().onConversationWith(npcId).build();
            return this;
        }
        
        public Builder triggeredBySecret(String npcId, int level) {
            this.trigger = CheckpointTrigger.npc().onSecretRevealed(npcId, level).build();
            return this;
        }
        
        public Builder triggeredByHTTP(String endpoint) {
            this.trigger = CheckpointTrigger.http().onEndpoint(endpoint).build();
            return this;
        }
        
        public Builder triggeredByRedstone(int x, int y, int z) {
            this.trigger = CheckpointTrigger.redstone().at(x, y, z).build();
            return this;
        }
        
        public Builder triggeredByArea(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.trigger = CheckpointTrigger.game().onEnterArea(x1, y1, z1, x2, y2, z2).build();
            return this;
        }
        
        public Builder triggeredByItem(String itemId) {
            this.trigger = CheckpointTrigger.game().onObtainItem(itemId).build();
            return this;
        }
        
        public Builder triggeredManually() {
            this.trigger = CheckpointTrigger.manual();
            return this;
        }
        
        public Builder requires(String... checkpointIds) {
            this.prerequisites = List.of(checkpointIds);
            return this;
        }
        
        public Builder unlocks(String... checkpointIds) {
            this.unlocks = List.of(checkpointIds);
            return this;
        }
        
        public Builder hintFrom(String npcId, String message) {
            this.hintNpcId = npcId;
            this.hintMessage = message;
            return this;
        }
        
        public Builder optional() {
            this.optional = true;
            return this;
        }
        
        public Builder hidden() {
            this.hidden = true;
            return this;
        }
        
        public Builder timeLimit(java.time.Duration limit) {
            this.timeLimit = limit;
            return this;
        }
        
        public Builder points(int points) {
            this.points = points;
            return this;
        }
        
        public Builder onComplete(CheckpointAction... actions) {
            this.onCompleteActions = List.of(actions);
            return this;
        }
        
        public Builder metadata(String key, Object value) {
            if (this.metadata.isEmpty()) {
                this.metadata = new java.util.HashMap<>();
            }
            ((java.util.HashMap<String, Object>) this.metadata).put(key, value);
            return this;
        }
        
        public Checkpoint build() {
            return new CheckpointImpl(
                id, name, description, trigger, prerequisites, unlocks,
                hintNpcId, hintMessage, optional, hidden, timeLimit,
                points, onCompleteActions, metadata
            );
        }
    }
}

/**
 * Action to execute when a checkpoint is completed.
 */
interface CheckpointAction {
    void execute(AdventureInstance instance, Checkpoint checkpoint);
    
    // Factory methods for common actions
    static CheckpointAction broadcast(String message) {
        return (instance, cp) -> instance.getTeam().broadcast(message);
    }
    
    static CheckpointAction webhook(String url) {
        return (instance, cp) -> {
            // Send HTTP POST to url with checkpoint data
        };
    }
    
    static CheckpointAction emitRedstone(int x, int y, int z, int strength, int duration) {
        return (instance, cp) -> {
            // Emit redstone signal
        };
    }
    
    static CheckpointAction npcSpeak(String npcId, String message) {
        return (instance, cp) -> {
            // Make NPC say something
        };
    }
    
    static CheckpointAction playSound(String sound) {
        return (instance, cp) -> instance.getTeam().playSound(null, 1.0f, 1.0f);
    }
    
    static CheckpointAction showTitle(String title, String subtitle) {
        return (instance, cp) -> instance.getTeam().showTitle(title, subtitle);
    }
    
    static CheckpointAction setProgress(String key, Object value) {
        return (instance, cp) -> instance.getTeam().getData().set(key, value);
    }
}

/**
 * Internal checkpoint implementation.
 */
record CheckpointImpl(
    String id,
    String name,
    String description,
    CheckpointTrigger trigger,
    List<String> prerequisites,
    List<String> unlocks,
    String hintNpcId,
    String hintMessage,
    boolean optional,
    boolean hidden,
    java.time.Duration timeLimit,
    int points,
    List<CheckpointAction> onCompleteActions,
    Map<String, Object> metadata
) implements Checkpoint {
    
    @Override
    public String getId() { return id; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public String getDescription() { return description; }
    
    @Override
    public CheckpointTrigger getTrigger() { return trigger; }
    
    @Override
    public List<String> getPrerequisites() { return prerequisites; }
    
    @Override
    public List<String> getUnlocks() { return unlocks; }
    
    @Override
    public Optional<String> getHintNpcId() { return Optional.ofNullable(hintNpcId); }
    
    @Override
    public Optional<String> getHintMessage() { return Optional.ofNullable(hintMessage); }
    
    @Override
    public boolean isOptional() { return optional; }
    
    @Override
    public boolean isHidden() { return hidden; }
    
    @Override
    public Optional<java.time.Duration> getTimeLimit() { return Optional.ofNullable(timeLimit); }
    
    @Override
    public int getPoints() { return points; }
    
    @Override
    public List<CheckpointAction> getOnCompleteActions() { return onCompleteActions; }
    
    @Override
    public Map<String, Object> getMetadata() { return metadata; }
}
