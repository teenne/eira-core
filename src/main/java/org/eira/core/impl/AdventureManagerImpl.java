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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adventure manager implementation that uses the external API server.
 *
 * <p>Adventure definitions and instances are stored on the API server.
 * This implementation caches data locally for performance.
 */
public class AdventureManagerImpl implements AdventureManager {

    private final EiraEventBus eventBus;
    private final TeamManagerImpl teamManager;
    private final ApiClient apiClient;

    // Local cache of adventures
    private final Map<String, AdventureImpl> adventureCache = new ConcurrentHashMap<>();
    private final Map<UUID, AdventureInstanceImpl> instanceCache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public AdventureManagerImpl(EiraEventBus eventBus, TeamManagerImpl teamManager, ApiClient apiClient) {
        this.eventBus = eventBus;
        this.teamManager = teamManager;
        this.apiClient = apiClient;
    }

    /**
     * Initialize with the Minecraft server instance.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Called every server tick for time tracking.
     */
    public void tick(MinecraftServer server) {
        // Update elapsed time for running instances
        for (AdventureInstanceImpl instance : instanceCache.values()) {
            if (instance.getState() == AdventureInstance.AdventureState.RUNNING) {
                instance.tick();
            }
        }
    }

    // ==================== AdventureManager Interface ====================

    @Override
    public void register(Adventure adventure) {
        if (adventure instanceof AdventureImpl impl) {
            adventureCache.put(adventure.getId(), impl);
        }
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
                    adventureCache.clear();

                    for (var elem : response.getData()) {
                        JsonObject json = elem.getAsJsonObject();
                        AdventureImpl adventure = AdventureImpl.fromJson(json);
                        adventureCache.put(adventure.getId(), adventure);
                    }

                    EiraCore.LOGGER.info("Loaded {} adventures from server", adventureCache.size());
                }
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to load adventures: {}", ex.getMessage());
                return null;
            });
    }

    @Override
    public AdventureInstance start(String adventureId, Team team) {
        AdventureImpl adventure = adventureCache.get(adventureId);
        if (adventure == null) {
            throw new IllegalArgumentException("Adventure not found: " + adventureId);
        }

        // Check team size
        if (team.getSize() < adventure.getMinTeamSize() || team.getSize() > adventure.getMaxTeamSize()) {
            throw new IllegalStateException("Team size must be between " +
                adventure.getMinTeamSize() + " and " + adventure.getMaxTeamSize());
        }

        // Create instance via API
        JsonObject request = new JsonObject();
        request.addProperty("adventureId", adventureId);
        request.addProperty("teamId", team.getId().toString());

        try {
            var response = apiClient.post("/adventures/" + adventureId + "/start", request, JsonObject.class).get();
            if (response.isSuccess() && response.getData() != null) {
                AdventureInstanceImpl instance = AdventureInstanceImpl.fromJson(
                    response.getData(), adventure, team, this
                );
                instanceCache.put(team.getId(), instance);

                eventBus.publish(new EiraEvents.AdventureStartedEvent(adventure, team, instance));
                return instance;
            } else {
                throw new RuntimeException("Failed to start adventure: " + response.getError());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to start adventure", e);
        }
    }

    @Override
    public Optional<AdventureInstance> getInstanceForTeam(Team team) {
        return Optional.ofNullable(instanceCache.get(team.getId()));
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
        // Save all active instances
        for (AdventureInstanceImpl instance : instanceCache.values()) {
            if (instance.getState() == AdventureInstance.AdventureState.RUNNING) {
                instance.syncToServer();
            }
        }
    }

    // ==================== Internal Methods ====================

    void completeCheckpointOnServer(AdventureInstanceImpl instance, String checkpointId) {
        JsonObject request = new JsonObject();
        request.addProperty("checkpointId", checkpointId);

        apiClient.post("/adventures/" + instance.getAdventure().getId() +
            "/instances/" + instance.getTeam().getId() + "/checkpoint", request, Void.class);
    }

    EiraEventBus getEventBus() {
        return eventBus;
    }

    ApiClient getApiClient() {
        return apiClient;
    }

    // ==================== Adventure Implementation ====================

    public static class AdventureImpl implements Adventure {
        private final String id;
        private final String name;
        private final AdventureType type;
        private final Duration timeLimit;
        private final int minTeamSize;
        private final int maxTeamSize;
        private final int maxTeams;
        private final List<Checkpoint> checkpoints = new ArrayList<>();

        public AdventureImpl(String id, String name, AdventureType type, Duration timeLimit,
                            int minTeamSize, int maxTeamSize, int maxTeams) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.timeLimit = timeLimit;
            this.minTeamSize = minTeamSize;
            this.maxTeamSize = maxTeamSize;
            this.maxTeams = maxTeams;
        }

        static AdventureImpl fromJson(JsonObject json) {
            String id = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString() : id;
            AdventureType type = json.has("type") ?
                AdventureType.valueOf(json.get("type").getAsString().toUpperCase()) :
                AdventureType.LINEAR;
            Duration timeLimit = json.has("timeLimitMinutes") ?
                Duration.ofMinutes(json.get("timeLimitMinutes").getAsLong()) :
                Duration.ZERO;
            int minTeamSize = json.has("minTeamSize") ? json.get("minTeamSize").getAsInt() : 1;
            int maxTeamSize = json.has("maxTeamSize") ? json.get("maxTeamSize").getAsInt() : 8;
            int maxTeams = json.has("maxTeams") ? json.get("maxTeams").getAsInt() : 999;

            AdventureImpl adventure = new AdventureImpl(id, name, type, timeLimit, minTeamSize, maxTeamSize, maxTeams);

            // Parse checkpoints if provided
            if (json.has("checkpoints") && json.get("checkpoints").isJsonArray()) {
                for (var elem : json.getAsJsonArray("checkpoints")) {
                    adventure.checkpoints.add(CheckpointImpl.fromJson(elem.getAsJsonObject()));
                }
            }

            return adventure;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public AdventureType getType() { return type; }
        @Override public Duration getTimeLimit() { return timeLimit; }
        @Override public int getMinTeamSize() { return minTeamSize; }
        @Override public int getMaxTeamSize() { return maxTeamSize; }
        @Override public int getMaxTeams() { return maxTeams; }
        @Override public List<Checkpoint> getCheckpoints() { return new ArrayList<>(checkpoints); }

        @Override
        public Checkpoint getCheckpoint(String checkpointId) {
            return checkpoints.stream()
                .filter(c -> c.getId().equals(checkpointId))
                .findFirst()
                .orElse(null);
        }
    }

    // ==================== Checkpoint Implementation ====================

    public static class CheckpointImpl implements Checkpoint {
        private final String id;
        private final String name;
        private final String description;

        public CheckpointImpl(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }

        static CheckpointImpl fromJson(JsonObject json) {
            String id = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString() : id;
            String description = json.has("description") ? json.get("description").getAsString() : "";
            return new CheckpointImpl(id, name, description);
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public List<String> getPrerequisites() { return Collections.emptyList(); }
        @Override public List<String> getUnlocks() { return Collections.emptyList(); }
        @Override public CheckpointTrigger getTrigger() { return null; }
        @Override public List<CheckpointAction> getOnComplete() { return Collections.emptyList(); }
        @Override public State getState() { return State.AVAILABLE; }
    }

    // ==================== Instance Implementation ====================

    public static class AdventureInstanceImpl implements AdventureInstance {
        private final AdventureImpl adventure;
        private final Team team;
        private final AdventureManagerImpl manager;

        private AdventureState state = AdventureState.RUNNING;
        private long startTimeMs;
        private long elapsedMs = 0;
        private long bonusMs = 0;
        private int currentCheckpointIndex = 0;
        private final Set<String> completedCheckpoints = new HashSet<>();

        public AdventureInstanceImpl(AdventureImpl adventure, Team team, AdventureManagerImpl manager) {
            this.adventure = adventure;
            this.team = team;
            this.manager = manager;
            this.startTimeMs = System.currentTimeMillis();
        }

        static AdventureInstanceImpl fromJson(JsonObject json, AdventureImpl adventure, Team team,
                                              AdventureManagerImpl manager) {
            AdventureInstanceImpl instance = new AdventureInstanceImpl(adventure, team, manager);

            if (json.has("state")) {
                instance.state = AdventureState.valueOf(json.get("state").getAsString());
            }
            if (json.has("elapsedMs")) {
                instance.elapsedMs = json.get("elapsedMs").getAsLong();
            }
            if (json.has("bonusMs")) {
                instance.bonusMs = json.get("bonusMs").getAsLong();
            }
            if (json.has("completedCheckpoints")) {
                for (var elem : json.getAsJsonArray("completedCheckpoints")) {
                    instance.completedCheckpoints.add(elem.getAsString());
                }
            }

            return instance;
        }

        void tick() {
            if (state == AdventureState.RUNNING) {
                elapsedMs = System.currentTimeMillis() - startTimeMs;

                // Check for timeout
                Duration timeLimit = adventure.getTimeLimit();
                if (!timeLimit.isZero() && getRemainingTime().isNegative()) {
                    fail("Time expired");
                }
            }
        }

        void syncToServer() {
            JsonObject request = new JsonObject();
            request.addProperty("state", state.name());
            request.addProperty("elapsedMs", elapsedMs);
            request.addProperty("bonusMs", bonusMs);

            JsonArray completed = new JsonArray();
            completedCheckpoints.forEach(completed::add);
            request.add("completedCheckpoints", completed);

            manager.getApiClient().put("/adventures/" + adventure.getId() +
                "/instances/" + team.getId(), request, Void.class);
        }

        @Override public Adventure getAdventure() { return adventure; }
        @Override public Team getTeam() { return team; }
        @Override public AdventureState getState() { return state; }

        @Override
        public float getProgress() {
            if (adventure.getCheckpoints().isEmpty()) return 0;
            return (float) completedCheckpoints.size() / adventure.getCheckpoints().size();
        }

        @Override
        public Duration getElapsedTime() {
            return Duration.ofMillis(elapsedMs);
        }

        @Override
        public Duration getRemainingTime() {
            Duration limit = adventure.getTimeLimit();
            if (limit.isZero()) return Duration.ZERO;
            return limit.plus(Duration.ofMillis(bonusMs)).minus(getElapsedTime());
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
            return adventure.getCheckpoints().stream()
                .filter(c -> completedCheckpoints.contains(c.getId()))
                .toList();
        }

        @Override
        public List<Checkpoint> getRemainingCheckpoints() {
            return adventure.getCheckpoints().stream()
                .filter(c -> !completedCheckpoints.contains(c.getId()))
                .toList();
        }

        @Override
        public void completeCheckpoint(String checkpointId) {
            if (completedCheckpoints.contains(checkpointId)) return;

            completedCheckpoints.add(checkpointId);
            currentCheckpointIndex++;

            manager.completeCheckpointOnServer(this, checkpointId);

            Checkpoint checkpoint = adventure.getCheckpoint(checkpointId);
            manager.getEventBus().publish(new EiraEvents.CheckpointReachedEvent(this, checkpoint, null));

            // Check if adventure complete
            if (completedCheckpoints.size() >= adventure.getCheckpoints().size()) {
                complete();
            }
        }

        @Override
        public void skipCheckpoint(String checkpointId) {
            completeCheckpoint(checkpointId);
        }

        @Override
        public void addTime(Duration bonus) {
            bonusMs += bonus.toMillis();
            syncToServer();
        }

        @Override
        public void complete() {
            state = AdventureState.COMPLETED;
            syncToServer();
            manager.getEventBus().publish(new EiraEvents.AdventureCompletedEvent(adventure, team, this));
        }

        @Override
        public void fail(String reason) {
            state = AdventureState.FAILED;
            syncToServer();
            manager.getEventBus().publish(new EiraEvents.AdventureFailedEvent(adventure, team, reason));
        }

        @Override
        public void reset() {
            state = AdventureState.RUNNING;
            startTimeMs = System.currentTimeMillis();
            elapsedMs = 0;
            bonusMs = 0;
            currentCheckpointIndex = 0;
            completedCheckpoints.clear();
            syncToServer();
        }
    }

    // ==================== Leaderboard Implementation ====================

    public static class LeaderboardImpl implements Leaderboard {
        private final String adventureId;
        private final ApiClient apiClient;
        private final List<Entry> entries = new ArrayList<>();

        public LeaderboardImpl(String adventureId, ApiClient apiClient) {
            this.adventureId = adventureId;
            this.apiClient = apiClient;
        }

        @Override
        public String getAdventureId() {
            return adventureId;
        }

        @Override
        public List<Entry> getTopEntries(int limit) {
            // Fetch from server
            try {
                var response = apiClient.get("/adventures/" + adventureId + "/leaderboard?limit=" + limit,
                    JsonArray.class).get();
                if (response.isSuccess() && response.getData() != null) {
                    List<Entry> result = new ArrayList<>();
                    for (var elem : response.getData()) {
                        result.add(EntryImpl.fromJson(elem.getAsJsonObject()));
                    }
                    return result;
                }
            } catch (Exception e) {
                EiraCore.LOGGER.error("Failed to fetch leaderboard: {}", e.getMessage());
            }
            return Collections.emptyList();
        }

        @Override
        public Optional<Entry> getEntry(String teamName) {
            return getTopEntries(100).stream()
                .filter(e -> e.getTeamName().equals(teamName))
                .findFirst();
        }

        @Override
        public int getRank(String teamName) {
            List<Entry> entries = getTopEntries(100);
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getTeamName().equals(teamName)) {
                    return i + 1;
                }
            }
            return -1;
        }
    }

    public static class EntryImpl implements Leaderboard.Entry {
        private final String teamName;
        private final Duration completionTime;
        private final java.time.Instant completedAt;
        private final int checkpointsCompleted;
        private final int totalCheckpoints;

        public EntryImpl(String teamName, Duration completionTime, java.time.Instant completedAt,
                        int checkpointsCompleted, int totalCheckpoints) {
            this.teamName = teamName;
            this.completionTime = completionTime;
            this.completedAt = completedAt;
            this.checkpointsCompleted = checkpointsCompleted;
            this.totalCheckpoints = totalCheckpoints;
        }

        static EntryImpl fromJson(JsonObject json) {
            String teamName = json.get("teamName").getAsString();
            Duration completionTime = Duration.ofMillis(json.get("completionTimeMs").getAsLong());
            java.time.Instant completedAt = java.time.Instant.ofEpochMilli(json.get("completedAt").getAsLong());
            int checkpointsCompleted = json.get("checkpointsCompleted").getAsInt();
            int totalCheckpoints = json.get("totalCheckpoints").getAsInt();
            return new EntryImpl(teamName, completionTime, completedAt, checkpointsCompleted, totalCheckpoints);
        }

        @Override public String getTeamName() { return teamName; }
        @Override public Duration getCompletionTime() { return completionTime; }
        @Override public java.time.Instant getCompletedAt() { return completedAt; }
        @Override public int getCheckpointsCompleted() { return checkpointsCompleted; }
        @Override public int getTotalCheckpoints() { return totalCheckpoints; }
    }
}
