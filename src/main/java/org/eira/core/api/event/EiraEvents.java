package org.eira.core.api.event;

import org.eira.core.api.team.Team;
import org.eira.core.api.adventure.AdventureInstance;
import org.eira.core.api.adventure.Checkpoint;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Built-in events for the Eira ecosystem.
 * Other mods can subscribe to these events.
 */
public final class EiraEvents {
    
    private EiraEvents() {}
    
    // ==================== Team Events ====================
    
    /**
     * Fired when a new team is created.
     */
    public record TeamCreatedEvent(Team team, @Nullable Player creator) implements EiraEvent {}
    
    /**
     * Fired when a team is disbanded.
     */
    public record TeamDisbandedEvent(Team team, DisbandReason reason) implements EiraEvent {
        public enum DisbandReason { MANUAL, EMPTY, EXPIRED, ADMIN }
    }
    
    /**
     * Fired when a player joins a team.
     */
    public record TeamMemberJoinedEvent(Team team, Player player) implements EiraEvent {}
    
    /**
     * Fired when a player leaves a team.
     */
    public record TeamMemberLeftEvent(Team team, Player player, LeaveReason reason) implements EiraEvent {
        public enum LeaveReason { LEFT, KICKED, DISCONNECTED, TEAM_DISBANDED }
    }
    
    /**
     * Fired when team data changes.
     */
    public record TeamDataChangedEvent(Team team, String key, Object oldValue, Object newValue) implements EiraEvent {}
    
    // ==================== Story Events ====================
    
    /**
     * Fired when a player starts a conversation with an NPC.
     */
    public record ConversationStartedEvent(
        UUID npcId, 
        Player player, 
        String characterId
    ) implements EiraEvent {}
    
    /**
     * Fired when a conversation ends.
     */
    public record ConversationEndedEvent(
        UUID npcId, 
        Player player, 
        int messageCount, 
        Duration duration
    ) implements EiraEvent {}
    
    /**
     * Fired when an NPC reveals a secret or hint.
     */
    public record SecretRevealedEvent(
        UUID npcId, 
        Player player, 
        String secretId,
        int revealLevel, 
        int maxLevel
    ) implements EiraEvent {}
    
    /**
     * Fired when a story chapter is unlocked.
     */
    public record ChapterUnlockedEvent(
        Player player, 
        String storyId, 
        String chapterId
    ) implements EiraEvent {}
    
    // ==================== Adventure Events ====================
    
    /**
     * Fired when an adventure starts for a team.
     */
    public record AdventureStartedEvent(
        AdventureInstance instance, 
        Team team
    ) implements EiraEvent {}
    
    /**
     * Fired when a checkpoint is reached.
     */
    public record CheckpointReachedEvent(
        AdventureInstance instance, 
        Checkpoint checkpoint,
        @Nullable Player triggeredBy
    ) implements EiraEvent {}
    
    /**
     * Fired when an adventure is completed successfully.
     */
    public record AdventureCompletedEvent(
        AdventureInstance instance, 
        Duration completionTime
    ) implements EiraEvent {}
    
    /**
     * Fired when an adventure fails.
     */
    public record AdventureFailedEvent(
        AdventureInstance instance, 
        String reason
    ) implements EiraEvent {}
    
    // ==================== External Events ====================
    
    /**
     * Fired when an HTTP request is received (from Eira Relay).
     */
    public record HttpReceivedEvent(
        String endpoint, 
        String method, 
        Map<String, String> params,
        Map<String, String> headers
    ) implements EiraEvent {}
    
    /**
     * Fired when an HTTP request is sent (from Eira Relay).
     */
    public record HttpSentEvent(
        String url, 
        String method, 
        int responseCode,
        boolean success
    ) implements EiraEvent {}
    
    /**
     * Fired for generic external triggers.
     */
    public record ExternalTriggerEvent(
        String source, 
        String triggerId, 
        Map<String, Object> data
    ) implements EiraEvent {}
    
    // ==================== Quest Events (for Eira Quest) ====================
    
    /**
     * Fired when a player starts a quest.
     */
    public record QuestStartedEvent(
        Player player, 
        String questId,
        @Nullable Team team
    ) implements EiraEvent {}
    
    /**
     * Fired when a quest objective is completed.
     */
    public record QuestObjectiveCompletedEvent(
        Player player, 
        String questId, 
        String objectiveId
    ) implements EiraEvent {}
    
    /**
     * Fired when a quest is completed.
     */
    public record QuestCompletedEvent(
        Player player, 
        String questId,
        @Nullable Team team
    ) implements EiraEvent {}
    
    // ==================== Player Events ====================
    
    /**
     * Fired when player progress is updated.
     */
    public record PlayerProgressEvent(
        Player player, 
        String key, 
        Object oldValue, 
        Object newValue
    ) implements EiraEvent {}
}
