package org.eira.core.impl;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.eira.core.EiraCore;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.player.EiraPlayer;
import org.eira.core.api.player.PlayerManager;
import org.eira.core.api.player.PlayerProgress;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player manager implementation that uses the external API server for persistence.
 *
 * <p>Progress and persistent data are stored on the API server.
 * Session data is kept locally and cleared on logout.
 */
public class PlayerManagerImpl implements PlayerManager {

    private final EiraEventBus eventBus;
    private final ApiClient apiClient;

    // Local cache of online players
    private final Map<UUID, EiraPlayerImpl> playerCache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public PlayerManagerImpl(EiraEventBus eventBus, ApiClient apiClient) {
        this.eventBus = eventBus;
        this.apiClient = apiClient;
    }

    /**
     * Initialize the player manager with the server instance.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    /**
     * Called when a player joins the server.
     */
    public void onPlayerJoin(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();

        // Fetch player data from API or create new
        apiClient.get("/players/" + playerId, JsonObject.class)
            .thenAccept(response -> {
                EiraPlayerImpl eiraPlayer;

                if (response.isSuccess() && response.getData() != null) {
                    eiraPlayer = EiraPlayerImpl.fromJson(response.getData(), this);
                } else {
                    // Create new player on server
                    eiraPlayer = new EiraPlayerImpl(playerId, playerName, this);
                    createPlayerOnServer(eiraPlayer);
                }

                eiraPlayer.setOnline(true);
                eiraPlayer.startSession();
                playerCache.put(playerId, eiraPlayer);

                EiraCore.LOGGER.debug("Player {} loaded", playerName);
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to load player {}: {}", playerName, ex.getMessage());
                // Create temporary local player
                EiraPlayerImpl eiraPlayer = new EiraPlayerImpl(playerId, playerName, this);
                eiraPlayer.setOnline(true);
                eiraPlayer.startSession();
                playerCache.put(playerId, eiraPlayer);
                return null;
            });
    }

    /**
     * Called when a player leaves the server.
     */
    public void onPlayerLeave(ServerPlayer player) {
        UUID playerId = player.getUUID();
        EiraPlayerImpl eiraPlayer = playerCache.get(playerId);

        if (eiraPlayer != null) {
            eiraPlayer.setOnline(false);
            eiraPlayer.endSession();

            // Save to server
            updatePlayerOnServer(eiraPlayer);

            // Keep in cache for a bit for potential quick reconnects
            // In production, you might want to evict after a delay
        }
    }

    // ==================== PlayerManager Interface ====================

    @Override
    public EiraPlayer get(Player player) {
        UUID playerId = player.getUUID();
        EiraPlayerImpl eiraPlayer = playerCache.get(playerId);

        if (eiraPlayer == null) {
            // Create temporary entry
            eiraPlayer = new EiraPlayerImpl(playerId, player.getName().getString(), this);
            eiraPlayer.setOnline(true);
            playerCache.put(playerId, eiraPlayer);
        }

        return eiraPlayer;
    }

    @Override
    public Optional<EiraPlayer> get(UUID playerId) {
        return Optional.ofNullable(playerCache.get(playerId));
    }

    @Override
    public boolean has(UUID playerId) {
        return playerCache.containsKey(playerId);
    }

    // ==================== Internal API Methods ====================

    private void createPlayerOnServer(EiraPlayerImpl player) {
        JsonObject request = player.toJson();
        apiClient.post("/players", request, Void.class);
    }

    void updatePlayerOnServer(EiraPlayerImpl player) {
        JsonObject request = player.toJson();
        apiClient.put("/players/" + player.getUUID(), request, Void.class);
    }

    void updateProgressOnServer(UUID playerId, PlayerProgressImpl progress) {
        JsonObject request = progress.toJson();
        apiClient.put("/players/" + playerId + "/progress", request, Void.class);
    }

    MinecraftServer getServer() {
        return server;
    }

    EiraEventBus getEventBus() {
        return eventBus;
    }

    ApiClient getApiClient() {
        return apiClient;
    }

    // ==================== EiraPlayer Implementation ====================

    /**
     * Extended player data implementation.
     */
    public static class EiraPlayerImpl implements EiraPlayer {
        private final UUID uuid;
        private final String name;
        private final PlayerManagerImpl manager;

        private final PlayerProgressImpl progress;
        private final PlayerProgressImpl sessionData;
        private final PlayerProgressImpl persistentData;

        private boolean online = false;
        private Instant sessionStart;
        private Instant lastActive;
        private final List<ActivityRecord> activityHistory = new ArrayList<>();

        EiraPlayerImpl(UUID uuid, String name, PlayerManagerImpl manager) {
            this.uuid = uuid;
            this.name = name;
            this.manager = manager;
            this.progress = new PlayerProgressImpl(this, "progress");
            this.sessionData = new PlayerProgressImpl(this, "session");
            this.persistentData = new PlayerProgressImpl(this, "persistent");
            this.lastActive = Instant.now();
        }

        static EiraPlayerImpl fromJson(JsonObject json, PlayerManagerImpl manager) {
            UUID uuid = UUID.fromString(json.get("uuid").getAsString());
            String name = json.has("name") ? json.get("name").getAsString() : "Unknown";

            EiraPlayerImpl player = new EiraPlayerImpl(uuid, name, manager);

            if (json.has("progress") && json.get("progress").isJsonObject()) {
                player.progress.loadFromJson(json.getAsJsonObject("progress"));
            }
            if (json.has("persistentData") && json.get("persistentData").isJsonObject()) {
                player.persistentData.loadFromJson(json.getAsJsonObject("persistentData"));
            }

            return player;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", uuid.toString());
            json.addProperty("name", name);
            json.add("progress", progress.toJson());
            json.add("persistentData", persistentData.toJson());
            return json;
        }

        void setOnline(boolean online) {
            this.online = online;
        }

        void startSession() {
            this.sessionStart = Instant.now();
            this.lastActive = Instant.now();
            this.sessionData.clear();
        }

        void endSession() {
            this.sessionStart = null;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Optional<Player> getPlayer() {
            if (manager.getServer() == null) return Optional.empty();
            return Optional.ofNullable(manager.getServer().getPlayerList().getPlayer(uuid));
        }

        @Override
        public boolean isOnline() {
            return online && getPlayer().isPresent();
        }

        @Override
        public PlayerProgress getProgress() {
            return progress;
        }

        @Override
        public PlayerProgress getSessionData() {
            return sessionData;
        }

        @Override
        public PlayerProgress getPersistentData() {
            return persistentData;
        }

        @Override
        public Duration getSessionTime() {
            if (sessionStart == null) return Duration.ZERO;
            return Duration.between(sessionStart, Instant.now());
        }

        @Override
        public Instant getLastActiveTime() {
            return lastActive;
        }

        @Override
        public void recordActivity(String action, Map<String, Object> data) {
            lastActive = Instant.now();
            activityHistory.add(new ActivityRecord(action, data, lastActive));

            // Keep last 100 activities
            while (activityHistory.size() > 100) {
                activityHistory.remove(0);
            }
        }

        void syncProgress() {
            manager.updateProgressOnServer(uuid, progress);
        }

        private record ActivityRecord(String action, Map<String, Object> data, Instant timestamp) {}
    }

    // ==================== PlayerProgress Implementation ====================

    /**
     * Player progress data implementation.
     */
    public static class PlayerProgressImpl implements PlayerProgress {
        private final EiraPlayerImpl player;
        private final String type;
        private final Map<String, Object> data = new ConcurrentHashMap<>();
        private final Map<String, Boolean> flags = new ConcurrentHashMap<>();

        PlayerProgressImpl(EiraPlayerImpl player, String type) {
            this.player = player;
            this.type = type;
        }

        void loadFromJson(JsonObject json) {
            data.clear();
            flags.clear();

            for (var entry : json.entrySet()) {
                var value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    var prim = value.getAsJsonPrimitive();
                    if (prim.isBoolean()) {
                        data.put(entry.getKey(), prim.getAsBoolean());
                    } else if (prim.isNumber()) {
                        data.put(entry.getKey(), prim.getAsNumber());
                    } else {
                        data.put(entry.getKey(), prim.getAsString());
                    }
                }
            }

            if (json.has("_flags") && json.get("_flags").isJsonObject()) {
                for (var entry : json.getAsJsonObject("_flags").entrySet()) {
                    flags.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            for (var entry : data.entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Boolean b) json.addProperty(entry.getKey(), b);
                else if (val instanceof Number n) json.addProperty(entry.getKey(), n);
                else if (val instanceof String s) json.addProperty(entry.getKey(), s);
            }

            if (!flags.isEmpty()) {
                JsonObject flagsJson = new JsonObject();
                for (var entry : flags.entrySet()) {
                    flagsJson.addProperty(entry.getKey(), entry.getValue());
                }
                json.add("_flags", flagsJson);
            }

            return json;
        }

        @Override
        public void set(String key, Object value) {
            data.put(key, value);
            if (!type.equals("session")) {
                player.syncProgress();
            }
        }

        @Override
        public Optional<Object> get(String key) {
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public Object get(String key, Object defaultValue) {
            return data.getOrDefault(key, defaultValue);
        }

        @Override
        public boolean has(String key) {
            return data.containsKey(key);
        }

        @Override
        public void remove(String key) {
            data.remove(key);
            if (!type.equals("session")) {
                player.syncProgress();
            }
        }

        @Override
        public void clear() {
            data.clear();
            flags.clear();
            if (!type.equals("session")) {
                player.syncProgress();
            }
        }

        @Override
        public Set<String> getKeys() {
            return new HashSet<>(data.keySet());
        }

        @Override
        public Map<String, Object> getAll() {
            return new HashMap<>(data);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = data.get(key);
            return value instanceof String s ? s : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = data.get(key);
            return value instanceof Number n ? n.intValue() : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = data.get(key);
            return value instanceof Number n ? n.longValue() : defaultValue;
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            Object value = data.get(key);
            return value instanceof Number n ? n.doubleValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = data.get(key);
            return value instanceof Boolean b ? b : defaultValue;
        }

        @Override
        public void setFlag(String key, boolean value) {
            flags.put(key, value);
            if (!type.equals("session")) {
                player.syncProgress();
            }
        }

        @Override
        public boolean getFlag(String key) {
            return flags.getOrDefault(key, false);
        }

        @Override
        public long increment(String key, long delta) {
            long current = getLong(key, 0);
            long newValue = current + delta;
            data.put(key, newValue);
            if (!type.equals("session")) {
                player.syncProgress();
            }
            return newValue;
        }
    }
}
