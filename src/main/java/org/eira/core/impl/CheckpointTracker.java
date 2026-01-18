package org.eira.core.impl;

import org.eira.core.EiraCore;
import org.eira.core.api.adventure.*;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.event.EiraEvents.*;
import org.eira.core.api.team.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks checkpoint progress and listens for trigger events.
 * 
 * <p>This class is the heart of the checkpoint system. It:
 * <ul>
 *   <li>Subscribes to all event types that can trigger checkpoints</li>
 *   <li>Maintains checkpoint state for each adventure instance</li>
 *   <li>Handles checkpoint dependencies and unlocking</li>
 *   <li>Executes completion actions</li>
 *   <li>Supports composite triggers (ALL_OF, ANY_OF, SEQUENCE)</li>
 * </ul>
 */
public class CheckpointTracker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("EiraCheckpoints");
    
    private final EiraEventBus eventBus;
    
    // Track checkpoint state per adventure instance
    private final Map<UUID, InstanceState> instanceStates = new ConcurrentHashMap<>();
    
    public CheckpointTracker(EiraEventBus eventBus) {
        this.eventBus = eventBus;
        subscribeToEvents();
    }
    
    /**
     * Initialize tracking for an adventure instance.
     */
    public void initializeInstance(AdventureInstance instance) {
        UUID instanceId = getInstanceId(instance);
        InstanceState state = new InstanceState(instance);
        
        // Initialize all checkpoints
        for (Checkpoint cp : instance.getAdventure().getCheckpoints()) {
            CheckpointState cpState = new CheckpointState(cp);
            
            // Determine initial state
            if (cp.getPrerequisites().isEmpty()) {
                cpState.state = Checkpoint.State.AVAILABLE;
            } else {
                cpState.state = Checkpoint.State.LOCKED;
            }
            
            state.checkpoints.put(cp.getId(), cpState);
        }
        
        instanceStates.put(instanceId, state);
        LOGGER.debug("Initialized checkpoint tracking for instance {}", instanceId);
    }
    
    /**
     * Clean up tracking for a finished instance.
     */
    public void cleanupInstance(AdventureInstance instance) {
        instanceStates.remove(getInstanceId(instance));
    }
    
    /**
     * Get the current state of a checkpoint.
     */
    public Checkpoint.State getCheckpointState(AdventureInstance instance, String checkpointId) {
        InstanceState state = instanceStates.get(getInstanceId(instance));
        if (state == null) return Checkpoint.State.LOCKED;
        
        CheckpointState cpState = state.checkpoints.get(checkpointId);
        return cpState != null ? cpState.state : Checkpoint.State.LOCKED;
    }
    
    /**
     * Get all available (completable) checkpoints for an instance.
     */
    public List<Checkpoint> getAvailableCheckpoints(AdventureInstance instance) {
        InstanceState state = instanceStates.get(getInstanceId(instance));
        if (state == null) return List.of();
        
        return state.checkpoints.values().stream()
            .filter(cs -> cs.state == Checkpoint.State.AVAILABLE || 
                         cs.state == Checkpoint.State.IN_PROGRESS)
            .map(cs -> cs.checkpoint)
            .toList();
    }
    
    /**
     * Manually complete a checkpoint (for MANUAL trigger type or admin override).
     */
    public boolean manualComplete(AdventureInstance instance, String checkpointId) {
        InstanceState state = instanceStates.get(getInstanceId(instance));
        if (state == null) return false;
        
        CheckpointState cpState = state.checkpoints.get(checkpointId);
        if (cpState == null) return false;
        
        if (cpState.state != Checkpoint.State.AVAILABLE && 
            cpState.state != Checkpoint.State.IN_PROGRESS) {
            return false;
        }
        
        completeCheckpoint(state, cpState, null);
        return true;
    }
    
    // ==================== Event Subscription ====================
    
    private void subscribeToEvents() {
        // NPC Events
        eventBus.subscribe(ConversationStartedEvent.class, this::onConversationStarted);
        eventBus.subscribe(ConversationEndedEvent.class, this::onConversationEnded);
        eventBus.subscribe(SecretRevealedEvent.class, this::onSecretRevealed);
        
        // HTTP Events (from Eira Relay)
        eventBus.subscribe(HttpReceivedEvent.class, this::onHttpReceived);
        eventBus.subscribe(ExternalTriggerEvent.class, this::onExternalTrigger);
        
        // Adventure Events (area enter, item obtain, etc. would be published by game event listeners)
        eventBus.subscribe(CheckpointReachedEvent.class, this::onCheckpointReached);
        
        LOGGER.info("CheckpointTracker subscribed to events");
    }
    
    private void onConversationStarted(ConversationStartedEvent event) {
        TriggerEvent trigger = new TriggerEvent(
            CheckpointTrigger.TriggerType.NPC_CONVERSATION_START,
            event.npcId().toString(),
            Map.of(
                "npcId", event.npcId().toString(),
                "characterId", event.characterId(),
                "playerId", event.player().getUUID().toString()
            )
        );
        
        checkAllInstances(trigger, event.player().getUUID());
    }
    
    private void onConversationEnded(ConversationEndedEvent event) {
        TriggerEvent trigger = new TriggerEvent(
            CheckpointTrigger.TriggerType.NPC_CONVERSATION_END,
            event.npcId().toString(),
            Map.of(
                "npcId", event.npcId().toString(),
                "messageCount", event.messageCount(),
                "playerId", event.player().getUUID().toString()
            )
        );
        
        checkAllInstances(trigger, event.player().getUUID());
    }
    
    private void onSecretRevealed(SecretRevealedEvent event) {
        TriggerEvent trigger = new TriggerEvent(
            CheckpointTrigger.TriggerType.NPC_SECRET_REVEALED,
            event.npcId().toString(),
            Map.of(
                "npcId", event.npcId().toString(),
                "secretId", event.secretId(),
                "secretLevel", event.revealLevel(),
                "maxLevel", event.maxLevel(),
                "playerId", event.player().getUUID().toString()
            )
        );
        
        checkAllInstances(trigger, event.player().getUUID());
    }
    
    private void onHttpReceived(HttpReceivedEvent event) {
        TriggerEvent trigger = new TriggerEvent(
            CheckpointTrigger.TriggerType.HTTP_ENDPOINT,
            event.endpoint(),
            Map.of(
                "endpoint", event.endpoint(),
                "method", event.method(),
                "params", event.params(),
                "headers", event.headers()
            )
        );
        
        // HTTP events might include team/player info in params
        String teamId = event.params().get("teamId");
        if (teamId != null) {
            checkInstancesForTeam(trigger, UUID.fromString(teamId));
        } else {
            // Check all instances
            checkAllInstances(trigger, null);
        }
    }
    
    private void onExternalTrigger(ExternalTriggerEvent event) {
        // Determine trigger type based on source
        CheckpointTrigger.TriggerType type = switch (event.source()) {
            case "redstone" -> CheckpointTrigger.TriggerType.REDSTONE_ON;
            case "qr_code", "nfc" -> CheckpointTrigger.TriggerType.HTTP_ENDPOINT;
            default -> CheckpointTrigger.TriggerType.CUSTOM;
        };
        
        TriggerEvent trigger = new TriggerEvent(type, event.triggerId(), event.data());
        checkAllInstances(trigger, null);
    }
    
    private void onCheckpointReached(CheckpointReachedEvent event) {
        // This is for checkpoints that were completed through other means
        // (e.g., area enter events from game listener)
        LOGGER.debug("Checkpoint reached via game event: {}", event.checkpoint().getId());
    }
    
    // ==================== Checkpoint Processing ====================
    
    private void checkAllInstances(TriggerEvent trigger, UUID playerId) {
        for (InstanceState state : instanceStates.values()) {
            // Check if this trigger is relevant to this instance
            if (playerId != null) {
                Team team = state.instance.getTeam();
                if (!team.hasMember(playerId)) continue;
            }
            
            checkTriggerForInstance(state, trigger);
        }
    }
    
    private void checkInstancesForTeam(TriggerEvent trigger, UUID teamId) {
        for (InstanceState state : instanceStates.values()) {
            if (state.instance.getTeam().getId().equals(teamId)) {
                checkTriggerForInstance(state, trigger);
            }
        }
    }
    
    private void checkTriggerForInstance(InstanceState state, TriggerEvent trigger) {
        for (CheckpointState cpState : state.checkpoints.values()) {
            // Only check available or in-progress checkpoints
            if (cpState.state != Checkpoint.State.AVAILABLE && 
                cpState.state != Checkpoint.State.IN_PROGRESS) {
                continue;
            }
            
            CheckpointTrigger cpTrigger = cpState.checkpoint.getTrigger();
            if (cpTrigger == null) continue;
            
            boolean matched = checkTriggerMatch(cpState, cpTrigger, trigger);
            
            if (matched) {
                LOGGER.debug("Checkpoint {} matched trigger {}", 
                    cpState.checkpoint.getId(), trigger.sourceType());
                completeCheckpoint(state, cpState, trigger);
            }
        }
    }
    
    private boolean checkTriggerMatch(CheckpointState cpState, CheckpointTrigger cpTrigger, TriggerEvent event) {
        // Handle composite triggers
        if (cpTrigger.getType() == CheckpointTrigger.TriggerType.ALL_OF) {
            return checkAllOfTrigger(cpState, (CompositeTrigger) cpTrigger, event);
        }
        if (cpTrigger.getType() == CheckpointTrigger.TriggerType.ANY_OF) {
            return checkAnyOfTrigger((CompositeTrigger) cpTrigger, event);
        }
        if (cpTrigger.getType() == CheckpointTrigger.TriggerType.SEQUENCE) {
            return checkSequenceTrigger(cpState, (CompositeTrigger) cpTrigger, event);
        }
        
        // Simple trigger
        return cpTrigger.matches(event);
    }
    
    private boolean checkAllOfTrigger(CheckpointState cpState, CompositeTrigger trigger, TriggerEvent event) {
        List<CheckpointTrigger> subTriggers = trigger.triggers();
        
        // Check if this event matches any sub-trigger
        for (int i = 0; i < subTriggers.size(); i++) {
            if (!cpState.completedSubTriggers.contains(i) && subTriggers.get(i).matches(event)) {
                cpState.completedSubTriggers.add(i);
                cpState.state = Checkpoint.State.IN_PROGRESS;
                LOGGER.debug("ALL_OF trigger: completed sub-trigger {} of {}", 
                    cpState.completedSubTriggers.size(), subTriggers.size());
            }
        }
        
        // Check if all completed
        return cpState.completedSubTriggers.size() >= subTriggers.size();
    }
    
    private boolean checkAnyOfTrigger(CompositeTrigger trigger, TriggerEvent event) {
        // Any sub-trigger matching is enough
        for (CheckpointTrigger subTrigger : trigger.triggers()) {
            if (subTrigger.matches(event)) {
                return true;
            }
        }
        return false;
    }
    
    private boolean checkSequenceTrigger(CheckpointState cpState, CompositeTrigger trigger, TriggerEvent event) {
        List<CheckpointTrigger> subTriggers = trigger.triggers();
        int nextIndex = cpState.completedSubTriggers.size();
        
        if (nextIndex >= subTriggers.size()) {
            return true; // Already completed
        }
        
        // Must match the next trigger in sequence
        if (subTriggers.get(nextIndex).matches(event)) {
            cpState.completedSubTriggers.add(nextIndex);
            cpState.state = Checkpoint.State.IN_PROGRESS;
            LOGGER.debug("SEQUENCE trigger: completed step {} of {}", 
                cpState.completedSubTriggers.size(), subTriggers.size());
            
            return cpState.completedSubTriggers.size() >= subTriggers.size();
        }
        
        return false;
    }
    
    private void completeCheckpoint(InstanceState state, CheckpointState cpState, TriggerEvent trigger) {
        cpState.state = Checkpoint.State.COMPLETED;
        cpState.completedAt = System.currentTimeMillis();
        
        Checkpoint checkpoint = cpState.checkpoint;
        AdventureInstance instance = state.instance;
        
        LOGGER.info("Checkpoint completed: {} (adventure: {})", 
            checkpoint.getId(), instance.getAdventure().getId());
        
        // Execute completion actions
        for (CheckpointAction action : checkpoint.getOnCompleteActions()) {
            try {
                action.execute(instance, checkpoint);
            } catch (Exception e) {
                LOGGER.error("Error executing checkpoint action", e);
            }
        }
        
        // Unlock dependent checkpoints
        for (String unlocksId : checkpoint.getUnlocks()) {
            CheckpointState dependentState = state.checkpoints.get(unlocksId);
            if (dependentState != null && dependentState.state == Checkpoint.State.LOCKED) {
                // Check if all prerequisites are now met
                if (arePrerequisitesMet(state, dependentState.checkpoint)) {
                    dependentState.state = Checkpoint.State.AVAILABLE;
                    LOGGER.debug("Unlocked checkpoint: {}", unlocksId);
                    
                    // Notify team
                    instance.getTeam().broadcast(
                        "§e[New Objective]§r " + dependentState.checkpoint.getName()
                    );
                }
            }
        }
        
        // Publish event
        eventBus.publish(new CheckpointReachedEvent(instance, checkpoint, null));
        
        // Check if adventure is complete
        checkAdventureCompletion(state);
    }
    
    private boolean arePrerequisitesMet(InstanceState state, Checkpoint checkpoint) {
        for (String prereqId : checkpoint.getPrerequisites()) {
            CheckpointState prereqState = state.checkpoints.get(prereqId);
            if (prereqState == null || prereqState.state != Checkpoint.State.COMPLETED) {
                return false;
            }
        }
        return true;
    }
    
    private void checkAdventureCompletion(InstanceState state) {
        // Check if all required (non-optional) checkpoints are completed
        boolean allRequired = state.checkpoints.values().stream()
            .filter(cs -> !cs.checkpoint.isOptional())
            .allMatch(cs -> cs.state == Checkpoint.State.COMPLETED);
        
        if (allRequired) {
            state.instance.complete();
        }
    }
    
    // ==================== Helper Classes ====================
    
    private UUID getInstanceId(AdventureInstance instance) {
        // Assuming instance has a unique ID
        return UUID.nameUUIDFromBytes(
            (instance.getAdventure().getId() + "_" + instance.getTeam().getId()).getBytes()
        );
    }
    
    private static class InstanceState {
        final AdventureInstance instance;
        final Map<String, CheckpointState> checkpoints = new ConcurrentHashMap<>();
        
        InstanceState(AdventureInstance instance) {
            this.instance = instance;
        }
    }
    
    private static class CheckpointState {
        final Checkpoint checkpoint;
        Checkpoint.State state = Checkpoint.State.LOCKED;
        long completedAt = 0;
        final Set<Integer> completedSubTriggers = new HashSet<>(); // For composite triggers
        
        CheckpointState(Checkpoint checkpoint) {
            this.checkpoint = checkpoint;
        }
    }
}
