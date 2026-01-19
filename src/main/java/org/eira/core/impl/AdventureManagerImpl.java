package org.eira.core.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import org.eira.core.EiraCore;
import org.eira.core.api.adventure.*;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.event.EiraEvents;
import org.eira.core.api.team.Team;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adventure manager implementation that uses the external API server.
 */
public class AdventureManagerImpl implements AdventureManager {

    private final EiraEventBus eventBus;
    private final TeamManagerImpl teamManager;
    private final ApiClient apiClient;

    private final Map<String, Adventure> adventureCache = new ConcurrentHashMap<>();
    private final Map<UUID, AdventureInstanceImpl> instanceCache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public AdventureManagerImpl(EiraEventBus eventBus, TeamManagerImpl teamManager, ApiClient apiClient) {
        this.eventBus = eventBus;
        this.teamManager = teamManager;
        this.apiClient = apiClient;
    }

    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    public void tick(MinecraftServer server) {
        for (AdventureInstanceImpl instance : instanceCache.values()) {
            if (instance.getState() == AdventureInstance.AdventureState.RUNNING) {
                instance.tick();
            }
        }
    }

    public EiraEventBus getEventBus() {
        return eventBus;
    }

    @Override
    public void register(Adventure adventure) {
        adventureCache.put(adventure.getId(), adventure);
    }

    @Override
    public Optional<Adventure> get(String adventureId) {
        return Optional.ofNullable(adventureCache.get(adventureId));
    }

    @Override
    public List<Adventure> getRegisteredAdventures() {
        return new ArrayList<>(adventureCache.values());
    }

    @Override
    public void loadAdventures() {
        apiClient.get("/adventures", JsonArray.class)
            .thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    EiraCore.LOGGER.info("Loaded adventures from server");
                }
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to load adventures: {}", ex.getMessage());
                return null;
            });
    }

    @Override
    public AdventureInstance start(String adventureId, Team team) {
        Adventure adventure = adventureCache.get(adventureId);
        if (adventure == null) {
            throw new IllegalArgumentException("Unknown adventure: " + adventureId);
        }

        UUID instanceId = UUID.randomUUID();
        AdventureInstanceImpl instance = new AdventureInstanceImpl(instanceId, adventure, team, this);
        instanceCache.put(instanceId, instance);

        // Notify API server
        JsonObject body = new JsonObject();
        body.addProperty("teamId", team.getId().toString());
        apiClient.post("/adventures/" + adventureId + "/start", body, JsonObject.class);

        eventBus.publish(new EiraEvents.AdventureStartedEvent(instance, team));

        return instance;
    }

    @Override
    public Optional<AdventureInstance> getInstanceForTeam(Team team) {
        return instanceCache.values().stream()
            .filter(i -> i.getTeam().getId().equals(team.getId()))
            .filter(i -> i.getState() == AdventureInstance.AdventureState.RUNNING)
            .map(i -> (AdventureInstance) i)
            .findFirst();
    }

    @Override
    public List<AdventureInstance> getActiveInstances() {
        return instanceCache.values().stream()
            .filter(i -> i.getState() == AdventureInstance.AdventureState.RUNNING)
            .map(i -> (AdventureInstance) i)
            .toList();
    }

    @Override
    public Leaderboard getLeaderboard(String adventureId) {
        return new LeaderboardImpl(adventureId, apiClient);
    }

    @Override
    public void shutdown() {
        for (AdventureInstanceImpl instance : instanceCache.values()) {
            if (instance.getState() == AdventureInstance.AdventureState.RUNNING) {
                instance.fail("Server shutdown");
            }
        }
        instanceCache.clear();
    }

    // ==================== AdventureInstance Implementation ====================

    public static class AdventureInstanceImpl implements AdventureInstance {
        private final UUID id;
        private final Adventure adventure;
        private final Team team;
        private final AdventureManagerImpl manager;
        private final long startTime;
        private long elapsedTime;
        private long bonusTime = 0;
        private AdventureState state = AdventureState.RUNNING;
        private final List<Checkpoint> completedCheckpoints = new ArrayList<>();
        private int currentCheckpointIndex = 0;

        public AdventureInstanceImpl(UUID id, Adventure adventure, Team team, AdventureManagerImpl manager) {
            this.id = id;
            this.adventure = adventure;
            this.team = team;
            this.manager = manager;
            this.startTime = System.currentTimeMillis();
        }

        void tick() {
            elapsedTime = System.currentTimeMillis() - startTime;
        }

        @Override
        public Adventure getAdventure() { return adventure; }

        @Override
        public Team getTeam() { return team; }

        @Override
        public AdventureState getState() { return state; }

        @Override
        public float getProgress() {
            List<Checkpoint> checkpoints = adventure.getCheckpoints();
            if (checkpoints.isEmpty()) return 1.0f;
            return (float) completedCheckpoints.size() / checkpoints.size();
        }

        @Override
        public Duration getElapsedTime() { return Duration.ofMillis(elapsedTime); }

        @Override
        public Duration getRemainingTime() {
            Duration limit = adventure.getTimeLimit();
            if (limit == null) return Duration.ZERO;
            long remaining = limit.toMillis() + bonusTime - elapsedTime;
            return Duration.ofMillis(Math.max(0, remaining));
        }

        @Override
        public Checkpoint getCurrentCheckpoint() {
            List<Checkpoint> checkpoints = adventure.getCheckpoints();
            if (currentCheckpointIndex < checkpoints.size()) {
                return checkpoints.get(currentCheckpointIndex);
            }
            return null;
        }

        @Override
        public List<Checkpoint> getCompletedCheckpoints() {
            return Collections.unmodifiableList(completedCheckpoints);
        }

        @Override
        public List<Checkpoint> getRemainingCheckpoints() {
            List<Checkpoint> all = adventure.getCheckpoints();
            if (currentCheckpointIndex >= all.size()) return Collections.emptyList();
            return all.subList(currentCheckpointIndex, all.size());
        }

        @Override
        public void completeCheckpoint(String checkpointId) {
            Checkpoint cp = adventure.getCheckpoint(checkpointId);
            if (cp != null && !completedCheckpoints.contains(cp)) {
                completedCheckpoints.add(cp);
                currentCheckpointIndex++;
            }
        }

        @Override
        public void skipCheckpoint(String checkpointId) {
            currentCheckpointIndex++;
        }

        @Override
        public void addTime(Duration bonus) {
            bonusTime += bonus.toMillis();
        }

        @Override
        public void complete() {
            state = AdventureState.COMPLETED;
            manager.getEventBus().publish(new EiraEvents.AdventureCompletedEvent(this, getElapsedTime()));
        }

        @Override
        public void fail(String reason) {
            state = AdventureState.FAILED;
            manager.getEventBus().publish(new EiraEvents.AdventureFailedEvent(this, reason));
        }

        @Override
        public void reset() {
            completedCheckpoints.clear();
            currentCheckpointIndex = 0;
            bonusTime = 0;
            state = AdventureState.RUNNING;
        }
    }

    // ==================== Leaderboard Implementation ====================

    public static class LeaderboardImpl implements Leaderboard {
        private final String adventureId;
        private final ApiClient apiClient;

        public LeaderboardImpl(String adventureId, ApiClient apiClient) {
            this.adventureId = adventureId;
            this.apiClient = apiClient;
        }

        @Override
        public String getAdventureId() { return adventureId; }

        @Override
        public List<LeaderboardEntry> getTop(int count) { return Collections.emptyList(); }

        @Override
        public Optional<Integer> getRank(Team team) { return Optional.empty(); }

        @Override
        public Optional<LeaderboardEntry> getEntry(Team team) { return Optional.empty(); }

        @Override
        public List<LeaderboardEntry> getEntriesAfter(Instant after) { return Collections.emptyList(); }

        @Override
        public int getEntryCount() { return 0; }
    }
}
