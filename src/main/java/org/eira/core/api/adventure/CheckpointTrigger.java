package org.eira.core.api.adventure;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Defines what triggers a checkpoint to complete.
 * 
 * <p>Checkpoints can be triggered by various event sources:
 * <ul>
 *   <li><b>Redstone events</b> - From Eira Relay blocks</li>
 *   <li><b>NPC events</b> - Conversations, secrets, clues</li>
 *   <li><b>HTTP events</b> - External API calls</li>
 *   <li><b>Game events</b> - Items, locations, interactions</li>
 *   <li><b>Composite</b> - Multiple conditions combined</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Complete when player talks to the oracle
 * CheckpointTrigger.npc()
 *     .onConversationWith("oracle")
 *     .withMinMessages(3)
 *     .build();
 * 
 * // Complete when QR code scanned
 * CheckpointTrigger.http()
 *     .onEndpoint("/qr/clue-1")
 *     .build();
 * 
 * // Complete when redstone activated at specific location
 * CheckpointTrigger.redstone()
 *     .onSignalAt(100, 64, 200)
 *     .withMinStrength(10)
 *     .build();
 * 
 * // Complete when ALL conditions met
 * CheckpointTrigger.all(
 *     CheckpointTrigger.npc().talkedTo("guard").build(),
 *     CheckpointTrigger.item().has("minecraft:key").build()
 * );
 * 
 * // Complete when ANY condition met
 * CheckpointTrigger.any(
 *     CheckpointTrigger.http().onEndpoint("/door/north").build(),
 *     CheckpointTrigger.http().onEndpoint("/door/south").build()
 * );
 * }</pre>
 */
public interface CheckpointTrigger {
    
    /**
     * Get the trigger type.
     */
    TriggerType getType();
    
    /**
     * Get trigger configuration.
     */
    Map<String, Object> getConfig();
    
    /**
     * Check if this trigger matches an event.
     */
    boolean matches(TriggerEvent event);
    
    /**
     * Get human-readable description.
     */
    String getDescription();
    
    // ==================== Trigger Types ====================
    
    enum TriggerType {
        // Redstone triggers (from Eira Relay)
        REDSTONE_ON,           // Redstone signal activated
        REDSTONE_OFF,          // Redstone signal deactivated
        REDSTONE_PULSE,        // Redstone pulse detected
        REDSTONE_PATTERN,      // Specific pattern detected
        
        // NPC triggers (from Eira NPC)
        NPC_CONVERSATION_START,  // Started talking to NPC
        NPC_CONVERSATION_END,    // Finished conversation
        NPC_SECRET_REVEALED,     // NPC revealed a secret
        NPC_CLUE_GIVEN,          // NPC gave a clue
        NPC_QUEST_ACCEPTED,      // Accepted quest from NPC
        NPC_MOOD_CHANGED,        // NPC mood changed to specific value
        
        // HTTP triggers (from Eira Relay / external)
        HTTP_RECEIVED,         // HTTP request received
        HTTP_ENDPOINT,         // Specific endpoint called
        HTTP_WITH_PARAM,       // Endpoint with specific parameter
        
        // Game triggers
        AREA_ENTER,            // Player entered area
        AREA_EXIT,             // Player left area
        ITEM_OBTAINED,         // Player got item
        ITEM_USED,             // Player used item
        BLOCK_INTERACT,        // Interacted with block
        ENTITY_INTERACT,       // Interacted with entity
        ENTITY_KILLED,         // Killed specific entity
        
        // Progress triggers
        PROGRESS_VALUE,        // Progress key reaches value
        FLAG_SET,              // Flag becomes true
        STORY_CHAPTER,         // Story chapter reached
        
        // Composite triggers
        ALL_OF,                // All sub-triggers must match
        ANY_OF,                // Any sub-trigger must match
        SEQUENCE,              // Sub-triggers in order
        TIMED,                 // Trigger within time limit
        
        // Manual/custom
        MANUAL,                // Manually triggered via API
        CUSTOM                 // Custom predicate
    }
    
    // ==================== Builder Factory Methods ====================
    
    /**
     * Create a redstone trigger builder.
     */
    static RedstoneTriggerBuilder redstone() {
        return new RedstoneTriggerBuilder();
    }
    
    /**
     * Create an NPC trigger builder.
     */
    static NPCTriggerBuilder npc() {
        return new NPCTriggerBuilder();
    }
    
    /**
     * Create an HTTP trigger builder.
     */
    static HTTPTriggerBuilder http() {
        return new HTTPTriggerBuilder();
    }
    
    /**
     * Create a game event trigger builder.
     */
    static GameTriggerBuilder game() {
        return new GameTriggerBuilder();
    }
    
    /**
     * Create a progress trigger builder.
     */
    static ProgressTriggerBuilder progress() {
        return new ProgressTriggerBuilder();
    }
    
    /**
     * Create a composite trigger that requires ALL conditions.
     */
    static CheckpointTrigger all(CheckpointTrigger... triggers) {
        return new CompositeTrigger(TriggerType.ALL_OF, List.of(triggers));
    }
    
    /**
     * Create a composite trigger that requires ANY condition.
     */
    static CheckpointTrigger any(CheckpointTrigger... triggers) {
        return new CompositeTrigger(TriggerType.ANY_OF, List.of(triggers));
    }
    
    /**
     * Create a sequence trigger (must happen in order).
     */
    static CheckpointTrigger sequence(CheckpointTrigger... triggers) {
        return new CompositeTrigger(TriggerType.SEQUENCE, List.of(triggers));
    }
    
    /**
     * Create a manual trigger (completed via API call).
     */
    static CheckpointTrigger manual() {
        return new ManualTrigger();
    }
    
    /**
     * Create a custom trigger with predicate.
     */
    static CheckpointTrigger custom(String description, Predicate<TriggerEvent> predicate) {
        return new CustomTrigger(description, predicate);
    }
    
    // ==================== Builders ====================
    
    class RedstoneTriggerBuilder {
        private TriggerType type = TriggerType.REDSTONE_ON;
        private Integer x, y, z;
        private Integer radius;
        private Integer minStrength = 1;
        private String blockId;
        private String pattern;
        
        public RedstoneTriggerBuilder onSignal() {
            this.type = TriggerType.REDSTONE_ON;
            return this;
        }
        
        public RedstoneTriggerBuilder onSignalOff() {
            this.type = TriggerType.REDSTONE_OFF;
            return this;
        }
        
        public RedstoneTriggerBuilder onPulse() {
            this.type = TriggerType.REDSTONE_PULSE;
            return this;
        }
        
        public RedstoneTriggerBuilder onPattern(String pattern) {
            this.type = TriggerType.REDSTONE_PATTERN;
            this.pattern = pattern;
            return this;
        }
        
        public RedstoneTriggerBuilder at(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }
        
        public RedstoneTriggerBuilder nearBlock(String blockId, int radius) {
            this.blockId = blockId;
            this.radius = radius;
            return this;
        }
        
        public RedstoneTriggerBuilder withMinStrength(int strength) {
            this.minStrength = strength;
            return this;
        }
        
        public CheckpointTrigger build() {
            return new RedstoneTrigger(type, x, y, z, radius, minStrength, blockId, pattern);
        }
    }
    
    class NPCTriggerBuilder {
        private TriggerType type = TriggerType.NPC_CONVERSATION_START;
        private String npcId;
        private String characterId;
        private Integer minMessages;
        private Integer secretLevel;
        private String clueId;
        private String mood;
        private String topic;
        
        public NPCTriggerBuilder onConversationWith(String npcId) {
            this.type = TriggerType.NPC_CONVERSATION_START;
            this.npcId = npcId;
            return this;
        }
        
        public NPCTriggerBuilder onConversationEnd(String npcId) {
            this.type = TriggerType.NPC_CONVERSATION_END;
            this.npcId = npcId;
            return this;
        }
        
        public NPCTriggerBuilder onSecretRevealed(String npcId) {
            this.type = TriggerType.NPC_SECRET_REVEALED;
            this.npcId = npcId;
            return this;
        }
        
        public NPCTriggerBuilder onSecretRevealed(String npcId, int minLevel) {
            this.type = TriggerType.NPC_SECRET_REVEALED;
            this.npcId = npcId;
            this.secretLevel = minLevel;
            return this;
        }
        
        public NPCTriggerBuilder onClueGiven(String npcId, String clueId) {
            this.type = TriggerType.NPC_CLUE_GIVEN;
            this.npcId = npcId;
            this.clueId = clueId;
            return this;
        }
        
        public NPCTriggerBuilder onMoodChanged(String npcId, String mood) {
            this.type = TriggerType.NPC_MOOD_CHANGED;
            this.npcId = npcId;
            this.mood = mood;
            return this;
        }
        
        public NPCTriggerBuilder withCharacter(String characterId) {
            this.characterId = characterId;
            return this;
        }
        
        public NPCTriggerBuilder withMinMessages(int count) {
            this.minMessages = count;
            return this;
        }
        
        public NPCTriggerBuilder aboutTopic(String topic) {
            this.topic = topic;
            return this;
        }
        
        public CheckpointTrigger build() {
            return new NPCTrigger(type, npcId, characterId, minMessages, secretLevel, clueId, mood, topic);
        }
    }
    
    class HTTPTriggerBuilder {
        private String endpoint;
        private String method = "POST";
        private Map<String, String> requiredParams;
        private Map<String, String> requiredHeaders;
        private String bodyContains;
        
        public HTTPTriggerBuilder onEndpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public HTTPTriggerBuilder withMethod(String method) {
            this.method = method;
            return this;
        }
        
        public HTTPTriggerBuilder withParam(String key, String value) {
            if (requiredParams == null) requiredParams = new java.util.HashMap<>();
            requiredParams.put(key, value);
            return this;
        }
        
        public HTTPTriggerBuilder withHeader(String key, String value) {
            if (requiredHeaders == null) requiredHeaders = new java.util.HashMap<>();
            requiredHeaders.put(key, value);
            return this;
        }
        
        public HTTPTriggerBuilder bodyContains(String text) {
            this.bodyContains = text;
            return this;
        }
        
        public CheckpointTrigger build() {
            return new HTTPTrigger(endpoint, method, requiredParams, requiredHeaders, bodyContains);
        }
    }
    
    class GameTriggerBuilder {
        private TriggerType type;
        private int[] areaMin, areaMax;
        private String itemId;
        private int itemCount = 1;
        private String blockId;
        private int[] blockPos;
        private String entityType;
        private String entityId;
        
        public GameTriggerBuilder onEnterArea(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.type = TriggerType.AREA_ENTER;
            this.areaMin = new int[]{x1, y1, z1};
            this.areaMax = new int[]{x2, y2, z2};
            return this;
        }
        
        public GameTriggerBuilder onExitArea(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.type = TriggerType.AREA_EXIT;
            this.areaMin = new int[]{x1, y1, z1};
            this.areaMax = new int[]{x2, y2, z2};
            return this;
        }
        
        public GameTriggerBuilder onObtainItem(String itemId) {
            this.type = TriggerType.ITEM_OBTAINED;
            this.itemId = itemId;
            return this;
        }
        
        public GameTriggerBuilder onObtainItem(String itemId, int count) {
            this.type = TriggerType.ITEM_OBTAINED;
            this.itemId = itemId;
            this.itemCount = count;
            return this;
        }
        
        public GameTriggerBuilder onUseItem(String itemId) {
            this.type = TriggerType.ITEM_USED;
            this.itemId = itemId;
            return this;
        }
        
        public GameTriggerBuilder onInteractBlock(String blockId, int x, int y, int z) {
            this.type = TriggerType.BLOCK_INTERACT;
            this.blockId = blockId;
            this.blockPos = new int[]{x, y, z};
            return this;
        }
        
        public GameTriggerBuilder onKillEntity(String entityType) {
            this.type = TriggerType.ENTITY_KILLED;
            this.entityType = entityType;
            return this;
        }
        
        public CheckpointTrigger build() {
            return new GameTrigger(type, areaMin, areaMax, itemId, itemCount, blockId, blockPos, entityType, entityId);
        }
    }
    
    class ProgressTriggerBuilder {
        private TriggerType type;
        private String key;
        private Object value;
        private String comparison = ">=";
        private String storyId;
        private String chapterId;
        
        public ProgressTriggerBuilder onValue(String key, String comparison, Object value) {
            this.type = TriggerType.PROGRESS_VALUE;
            this.key = key;
            this.comparison = comparison;
            this.value = value;
            return this;
        }
        
        public ProgressTriggerBuilder onValueReached(String key, int value) {
            return onValue(key, ">=", value);
        }
        
        public ProgressTriggerBuilder onFlag(String flag) {
            this.type = TriggerType.FLAG_SET;
            this.key = flag;
            this.value = true;
            return this;
        }
        
        public ProgressTriggerBuilder onStoryChapter(String storyId, String chapterId) {
            this.type = TriggerType.STORY_CHAPTER;
            this.storyId = storyId;
            this.chapterId = chapterId;
            return this;
        }
        
        public CheckpointTrigger build() {
            return new ProgressTrigger(type, key, value, comparison, storyId, chapterId);
        }
    }
}

// ==================== Trigger Event (passed to matchers) ====================

/**
 * Represents an event that might trigger a checkpoint.
 */
record TriggerEvent(
    CheckpointTrigger.TriggerType sourceType,
    String sourceId,
    Map<String, Object> data
) {
    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }
    
    public <T> T getData(String key, T defaultValue) {
        Object value = data.get(key);
        return value != null ? (T) value : defaultValue;
    }
}

// ==================== Trigger Implementations ====================

record RedstoneTrigger(
    CheckpointTrigger.TriggerType type,
    Integer x, Integer y, Integer z,
    Integer radius,
    Integer minStrength,
    String blockId,
    String pattern
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return type; }
    
    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        if (x != null) config.put("x", x);
        if (y != null) config.put("y", y);
        if (z != null) config.put("z", z);
        if (radius != null) config.put("radius", radius);
        if (minStrength != null) config.put("minStrength", minStrength);
        if (blockId != null) config.put("blockId", blockId);
        if (pattern != null) config.put("pattern", pattern);
        return config;
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        if (!event.sourceType().name().startsWith("REDSTONE")) return false;
        
        // Check position if specified
        if (x != null && y != null && z != null) {
            Integer ex = event.getData("x", Integer.class);
            Integer ey = event.getData("y", Integer.class);
            Integer ez = event.getData("z", Integer.class);
            if (ex == null || ey == null || ez == null) return false;
            
            if (radius != null) {
                double dist = Math.sqrt(Math.pow(ex - x, 2) + Math.pow(ey - y, 2) + Math.pow(ez - z, 2));
                if (dist > radius) return false;
            } else {
                if (!ex.equals(x) || !ey.equals(y) || !ez.equals(z)) return false;
            }
        }
        
        // Check strength
        if (minStrength != null) {
            Integer strength = event.getData("strength", Integer.class);
            if (strength == null || strength < minStrength) return false;
        }
        
        return true;
    }
    
    @Override
    public String getDescription() {
        if (x != null) {
            return String.format("Redstone signal at (%d, %d, %d)", x, y, z);
        }
        return "Redstone signal detected";
    }
}

record NPCTrigger(
    CheckpointTrigger.TriggerType type,
    String npcId,
    String characterId,
    Integer minMessages,
    Integer secretLevel,
    String clueId,
    String mood,
    String topic
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return type; }
    
    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        if (npcId != null) config.put("npcId", npcId);
        if (characterId != null) config.put("characterId", characterId);
        if (minMessages != null) config.put("minMessages", minMessages);
        if (secretLevel != null) config.put("secretLevel", secretLevel);
        if (clueId != null) config.put("clueId", clueId);
        if (mood != null) config.put("mood", mood);
        if (topic != null) config.put("topic", topic);
        return config;
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        if (!event.sourceType().name().startsWith("NPC_")) return false;
        
        // Check NPC ID
        if (npcId != null) {
            String eventNpc = event.getData("npcId", String.class);
            if (!npcId.equals(eventNpc)) return false;
        }
        
        // Check character
        if (characterId != null) {
            String eventChar = event.getData("characterId", String.class);
            if (!characterId.equals(eventChar)) return false;
        }
        
        // Check message count
        if (minMessages != null) {
            Integer count = event.getData("messageCount", Integer.class);
            if (count == null || count < minMessages) return false;
        }
        
        // Check secret level
        if (secretLevel != null) {
            Integer level = event.getData("secretLevel", Integer.class);
            if (level == null || level < secretLevel) return false;
        }
        
        return true;
    }
    
    @Override
    public String getDescription() {
        return switch (type) {
            case NPC_CONVERSATION_START -> "Talk to " + (npcId != null ? npcId : "an NPC");
            case NPC_SECRET_REVEALED -> "Learn secret from " + (npcId != null ? npcId : "an NPC");
            case NPC_CLUE_GIVEN -> "Get clue from " + (npcId != null ? npcId : "an NPC");
            default -> "NPC interaction";
        };
    }
}

record HTTPTrigger(
    String endpoint,
    String method,
    Map<String, String> requiredParams,
    Map<String, String> requiredHeaders,
    String bodyContains
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return TriggerType.HTTP_ENDPOINT; }
    
    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("endpoint", endpoint);
        config.put("method", method);
        if (requiredParams != null) config.put("params", requiredParams);
        if (requiredHeaders != null) config.put("headers", requiredHeaders);
        if (bodyContains != null) config.put("bodyContains", bodyContains);
        return config;
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        if (event.sourceType() != TriggerType.HTTP_RECEIVED && 
            event.sourceType() != TriggerType.HTTP_ENDPOINT) return false;
        
        // Check endpoint
        String eventEndpoint = event.getData("endpoint", String.class);
        if (endpoint != null && !endpoint.equals(eventEndpoint)) return false;
        
        // Check method
        String eventMethod = event.getData("method", String.class);
        if (method != null && !method.equalsIgnoreCase(eventMethod)) return false;
        
        // Check params
        if (requiredParams != null) {
            @SuppressWarnings("unchecked")
            Map<String, String> eventParams = (Map<String, String>) event.data().get("params");
            if (eventParams == null) return false;
            for (var entry : requiredParams.entrySet()) {
                if (!entry.getValue().equals(eventParams.get(entry.getKey()))) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    @Override
    public String getDescription() {
        return "HTTP " + method + " to " + endpoint;
    }
}

record GameTrigger(
    CheckpointTrigger.TriggerType type,
    int[] areaMin,
    int[] areaMax,
    String itemId,
    int itemCount,
    String blockId,
    int[] blockPos,
    String entityType,
    String entityId
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return type; }
    
    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        if (areaMin != null) config.put("areaMin", areaMin);
        if (areaMax != null) config.put("areaMax", areaMax);
        if (itemId != null) config.put("itemId", itemId);
        if (itemCount > 1) config.put("itemCount", itemCount);
        if (blockId != null) config.put("blockId", blockId);
        if (blockPos != null) config.put("blockPos", blockPos);
        if (entityType != null) config.put("entityType", entityType);
        return config;
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        // Implementation would check against actual game events
        return event.sourceType() == type;
    }
    
    @Override
    public String getDescription() {
        return switch (type) {
            case AREA_ENTER -> "Enter the designated area";
            case ITEM_OBTAINED -> "Obtain " + itemId;
            case ENTITY_KILLED -> "Defeat " + entityType;
            default -> "Game objective";
        };
    }
}

record ProgressTrigger(
    CheckpointTrigger.TriggerType type,
    String key,
    Object value,
    String comparison,
    String storyId,
    String chapterId
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return type; }
    
    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new java.util.HashMap<>();
        if (key != null) config.put("key", key);
        if (value != null) config.put("value", value);
        if (comparison != null) config.put("comparison", comparison);
        if (storyId != null) config.put("storyId", storyId);
        if (chapterId != null) config.put("chapterId", chapterId);
        return config;
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        return event.sourceType() == type;
    }
    
    @Override
    public String getDescription() {
        if (type == TriggerType.FLAG_SET) {
            return "Complete: " + key;
        }
        if (type == TriggerType.STORY_CHAPTER) {
            return "Reach chapter " + chapterId;
        }
        return "Progress: " + key + " " + comparison + " " + value;
    }
}

record CompositeTrigger(
    CheckpointTrigger.TriggerType type,
    List<CheckpointTrigger> triggers
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return type; }
    
    @Override
    public Map<String, Object> getConfig() {
        return Map.of("triggers", triggers);
    }
    
    @Override
    public boolean matches(TriggerEvent event) {
        // Note: Composite triggers need special handling in the adventure manager
        // They track partial progress across multiple events
        return false;
    }
    
    @Override
    public String getDescription() {
        return switch (type) {
            case ALL_OF -> "Complete all: " + triggers.size() + " objectives";
            case ANY_OF -> "Complete any: " + triggers.size() + " objectives";
            case SEQUENCE -> "Complete in order: " + triggers.size() + " steps";
            default -> "Multiple objectives";
        };
    }
}

record ManualTrigger() implements CheckpointTrigger {
    @Override
    public TriggerType getType() { return TriggerType.MANUAL; }
    
    @Override
    public Map<String, Object> getConfig() { return Map.of(); }
    
    @Override
    public boolean matches(TriggerEvent event) {
        return event.sourceType() == TriggerType.MANUAL;
    }
    
    @Override
    public String getDescription() { return "Manual checkpoint"; }
}

record CustomTrigger(
    String description,
    Predicate<TriggerEvent> predicate
) implements CheckpointTrigger {
    
    @Override
    public TriggerType getType() { return TriggerType.CUSTOM; }
    
    @Override
    public Map<String, Object> getConfig() { return Map.of("description", description); }
    
    @Override
    public boolean matches(TriggerEvent event) {
        return predicate.test(event);
    }
    
    @Override
    public String getDescription() { return description; }
}
