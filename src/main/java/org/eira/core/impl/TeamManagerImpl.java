package org.eira.core.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.eira.core.EiraCore;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.event.EiraEvents;
import org.eira.core.api.team.Team;
import org.eira.core.api.team.TeamData;
import org.eira.core.api.team.TeamManager;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Team manager implementation that uses the external API server for persistence.
 *
 * <p>All team data is stored on the API server. This implementation maintains
 * a local cache for performance but always syncs with the server for mutations.
 */
public class TeamManagerImpl implements TeamManager {

    private final EiraEventBus eventBus;
    private final ApiClient apiClient;

    // Local cache of teams (synced from server)
    private final Map<UUID, TeamImpl> teamCache = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerTeamCache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public TeamManagerImpl(EiraEventBus eventBus, ApiClient apiClient) {
        this.eventBus = eventBus;
        this.apiClient = apiClient;
    }

    /**
     * Initialize the team manager with the server instance.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
        refreshCache();
    }

    /**
     * Refresh the local cache from the API server.
     */
    public CompletableFuture<Void> refreshCache() {
        return apiClient.get("/teams", JsonArray.class)
            .thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    teamCache.clear();
                    playerTeamCache.clear();

                    for (var element : response.getData()) {
                        JsonObject json = element.getAsJsonObject();
                        TeamImpl team = TeamImpl.fromJson(json, this);
                        teamCache.put(team.getId(), team);

                        // Update player -> team mapping
                        for (UUID memberId : team.getMemberIds()) {
                            playerTeamCache.put(memberId, team.getId());
                        }
                    }

                    EiraCore.LOGGER.info("Team cache refreshed: {} teams loaded", teamCache.size());
                }
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to refresh team cache: {}", ex.getMessage());
                return null;
            });
    }

    // ==================== TeamManager Interface ====================

    @Override
    public TeamBuilder create(String name) {
        return new TeamBuilderImpl(name, this);
    }

    @Override
    public Optional<Team> getById(UUID id) {
        return Optional.ofNullable(teamCache.get(id));
    }

    @Override
    public Optional<Team> getByName(String name) {
        return teamCache.values().stream()
            .filter(team -> team.getName().equalsIgnoreCase(name))
            .map(team -> (Team) team)
            .findFirst();
    }

    @Override
    public List<Team> getAll() {
        return new ArrayList<>(teamCache.values());
    }

    @Override
    public List<Team> getByTag(String tag) {
        return teamCache.values().stream()
            .filter(team -> tag.equals(team.getTag()))
            .collect(Collectors.toList());
    }

    @Override
    public Optional<Team> getTeamOf(Player player) {
        return getTeamOf(player.getUUID());
    }

    @Override
    public Optional<Team> getTeamOf(UUID playerId) {
        UUID teamId = playerTeamCache.get(playerId);
        if (teamId != null) {
            return Optional.ofNullable(teamCache.get(teamId));
        }
        return Optional.empty();
    }

    @Override
    public boolean hasTeam(Player player) {
        return playerTeamCache.containsKey(player.getUUID());
    }

    @Override
    public boolean remove(UUID teamId) {
        TeamImpl team = teamCache.get(teamId);
        if (team == null) {
            return false;
        }

        apiClient.delete("/teams/" + teamId, Void.class)
            .thenAccept(response -> {
                if (response.isSuccess()) {
                    teamCache.remove(teamId);
                    team.getMemberIds().forEach(playerTeamCache::remove);
                    eventBus.publish(new EiraEvents.TeamDisbandedEvent(team));
                }
            });

        return true;
    }

    @Override
    public int getTeamCount() {
        return teamCache.size();
    }

    // ==================== Internal API Methods ====================

    CompletableFuture<TeamImpl> createTeamOnServer(TeamBuilderImpl builder) {
        JsonObject request = new JsonObject();
        request.addProperty("name", builder.name);
        request.addProperty("color", builder.color.getName());
        request.addProperty("maxSize", builder.maxSize);
        if (builder.tag != null) {
            request.addProperty("tag", builder.tag);
        }
        if (builder.leaderId != null) {
            request.addProperty("leaderId", builder.leaderId.toString());
        }
        if (!builder.memberIds.isEmpty()) {
            JsonArray members = new JsonArray();
            builder.memberIds.forEach(id -> members.add(id.toString()));
            request.add("memberIds", members);
        }
        if (!builder.initialData.isEmpty()) {
            request.add("data", apiClient.getGson().toJsonTree(builder.initialData));
        }

        return apiClient.post("/teams", request, JsonObject.class)
            .thenApply(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    TeamImpl team = TeamImpl.fromJson(response.getData(), this);
                    teamCache.put(team.getId(), team);

                    // Update player -> team mapping
                    for (UUID memberId : team.getMemberIds()) {
                        playerTeamCache.put(memberId, team.getId());
                    }

                    eventBus.publish(new EiraEvents.TeamCreatedEvent(team));
                    return team;
                } else {
                    throw new RuntimeException("Failed to create team: " + response.getError());
                }
            });
    }

    void updateTeamOnServer(TeamImpl team) {
        JsonObject request = team.toJson();
        apiClient.put("/teams/" + team.getId(), request, Void.class);
    }

    void addMemberOnServer(TeamImpl team, UUID playerId) {
        JsonObject request = new JsonObject();
        request.addProperty("playerId", playerId.toString());

        apiClient.post("/teams/" + team.getId() + "/members", request, Void.class)
            .thenAccept(response -> {
                if (response.isSuccess()) {
                    playerTeamCache.put(playerId, team.getId());
                }
            });
    }

    void removeMemberOnServer(TeamImpl team, UUID playerId) {
        apiClient.delete("/teams/" + team.getId() + "/members/" + playerId, Void.class)
            .thenAccept(response -> {
                if (response.isSuccess()) {
                    playerTeamCache.remove(playerId);
                }
            });
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

    // ==================== Team Builder Implementation ====================

    private static class TeamBuilderImpl implements TeamBuilder {
        private final String name;
        private final TeamManagerImpl manager;

        private ChatFormatting color = ChatFormatting.WHITE;
        private int maxSize = 8;
        private String tag = null;
        private UUID leaderId = null;
        private final List<UUID> memberIds = new ArrayList<>();
        private final Map<String, Object> initialData = new HashMap<>();

        TeamBuilderImpl(String name, TeamManagerImpl manager) {
            this.name = name;
            this.manager = manager;
        }

        @Override
        public TeamBuilder withColor(ChatFormatting color) {
            this.color = color;
            return this;
        }

        @Override
        public TeamBuilder withMaxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        @Override
        public TeamBuilder withTag(String tag) {
            this.tag = tag;
            return this;
        }

        @Override
        public TeamBuilder withData(String key, Object value) {
            this.initialData.put(key, value);
            return this;
        }

        @Override
        public TeamBuilder withLeader(Player leader) {
            this.leaderId = leader.getUUID();
            if (!memberIds.contains(leaderId)) {
                memberIds.add(leaderId);
            }
            return this;
        }

        @Override
        public TeamBuilder withMembers(Player... members) {
            for (Player member : members) {
                UUID id = member.getUUID();
                if (!memberIds.contains(id)) {
                    memberIds.add(id);
                }
            }
            return this;
        }

        @Override
        public Team build() {
            // Block and wait for server response
            try {
                return manager.createTeamOnServer(this).get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create team", e);
            }
        }
    }

    // ==================== Team Implementation ====================

    /**
     * Team implementation backed by API server data.
     */
    public static class TeamImpl implements Team {
        private final UUID id;
        private final TeamManagerImpl manager;
        private final TeamDataImpl data;

        private String name;
        private ChatFormatting color;
        private int maxSize;
        private String tag;
        private UUID leaderId;
        private final List<UUID> memberIds = new ArrayList<>();
        private boolean disbanded = false;

        private TeamImpl(UUID id, TeamManagerImpl manager) {
            this.id = id;
            this.manager = manager;
            this.data = new TeamDataImpl(this);
        }

        static TeamImpl fromJson(JsonObject json, TeamManagerImpl manager) {
            UUID id = UUID.fromString(json.get("id").getAsString());
            TeamImpl team = new TeamImpl(id, manager);

            team.name = json.get("name").getAsString();
            team.color = ChatFormatting.getByName(json.get("color").getAsString());
            if (team.color == null) team.color = ChatFormatting.WHITE;
            team.maxSize = json.has("maxSize") ? json.get("maxSize").getAsInt() : 8;
            team.tag = json.has("tag") && !json.get("tag").isJsonNull() ?
                json.get("tag").getAsString() : null;
            team.leaderId = json.has("leaderId") && !json.get("leaderId").isJsonNull() ?
                UUID.fromString(json.get("leaderId").getAsString()) : null;

            if (json.has("memberIds")) {
                for (var elem : json.getAsJsonArray("memberIds")) {
                    team.memberIds.add(UUID.fromString(elem.getAsString()));
                }
            }

            if (json.has("data") && json.get("data").isJsonObject()) {
                team.data.loadFromJson(json.getAsJsonObject("data"));
            }

            return team;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("name", name);
            json.addProperty("color", color.getName());
            json.addProperty("maxSize", maxSize);
            if (tag != null) json.addProperty("tag", tag);
            if (leaderId != null) json.addProperty("leaderId", leaderId.toString());

            JsonArray members = new JsonArray();
            memberIds.forEach(mid -> members.add(mid.toString()));
            json.add("memberIds", members);

            json.add("data", data.toJson());
            return json;
        }

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
            manager.updateTeamOnServer(this);
        }

        @Override
        public ChatFormatting getColor() {
            return color;
        }

        @Override
        public void setColor(ChatFormatting color) {
            this.color = color;
            manager.updateTeamOnServer(this);
        }

        @Override
        public int getMaxSize() {
            return maxSize;
        }

        @Override
        public int getSize() {
            return memberIds.size();
        }

        @Override
        public List<UUID> getMemberIds() {
            return new ArrayList<>(memberIds);
        }

        @Override
        public List<Player> getOnlineMembers() {
            MinecraftServer server = manager.getServer();
            if (server == null) return Collections.emptyList();

            return memberIds.stream()
                .map(id -> server.getPlayerList().getPlayer(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        @Override
        public boolean hasMember(Player player) {
            return hasMember(player.getUUID());
        }

        @Override
        public boolean hasMember(UUID playerId) {
            return memberIds.contains(playerId);
        }

        @Override
        public boolean addMember(Player player) {
            if (isFull() || hasMember(player)) {
                return false;
            }

            UUID playerId = player.getUUID();
            memberIds.add(playerId);
            manager.addMemberOnServer(this, playerId);
            manager.getEventBus().publish(new EiraEvents.TeamMemberJoinedEvent(this, player));
            return true;
        }

        @Override
        public boolean removeMember(Player player) {
            UUID playerId = player.getUUID();
            if (!memberIds.remove(playerId)) {
                return false;
            }

            manager.removeMemberOnServer(this, playerId);
            manager.getEventBus().publish(new EiraEvents.TeamMemberLeftEvent(
                this, player, EiraEvents.TeamMemberLeftEvent.LeaveReason.REMOVED
            ));
            return true;
        }

        @Override
        @Nullable
        public UUID getLeaderId() {
            return leaderId;
        }

        @Override
        public Optional<Player> getLeader() {
            if (leaderId == null || manager.getServer() == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(manager.getServer().getPlayerList().getPlayer(leaderId));
        }

        @Override
        public boolean isLeader(Player player) {
            return leaderId != null && leaderId.equals(player.getUUID());
        }

        @Override
        public void setLeader(Player player) {
            this.leaderId = player.getUUID();
            if (!hasMember(player)) {
                addMember(player);
            }
            manager.updateTeamOnServer(this);
        }

        @Override
        public TeamData getData() {
            return data;
        }

        @Override
        @Nullable
        public String getTag() {
            return tag;
        }

        @Override
        public void setTag(@Nullable String tag) {
            this.tag = tag;
            manager.updateTeamOnServer(this);
        }

        // Communication methods
        @Override
        public void broadcast(String message) {
            broadcast(Component.literal(message));
        }

        @Override
        public void broadcast(Component message) {
            for (Player player : getOnlineMembers()) {
                player.sendSystemMessage(message);
            }
        }

        @Override
        public void sendTo(Player player, String message) {
            player.sendSystemMessage(Component.literal(message));
        }

        @Override
        public void playSound(SoundEvent sound, float volume, float pitch) {
            for (Player player : getOnlineMembers()) {
                if (player instanceof ServerPlayer sp) {
                    sp.playSound(sound, volume, pitch);
                }
            }
        }

        @Override
        public void showTitle(String title, String subtitle) {
            showTitle(title, subtitle, 10, 70, 20);
        }

        @Override
        public void showTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            for (Player player : getOnlineMembers()) {
                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(title)
                    ));
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal(subtitle)
                    ));
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                        fadeIn, stay, fadeOut
                    ));
                }
            }
        }

        @Override
        public void showActionBar(String message) {
            for (Player player : getOnlineMembers()) {
                if (player instanceof ServerPlayer sp) {
                    sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket(
                        Component.literal(message)
                    ));
                }
            }
        }

        // Proximity methods
        @Override
        public boolean areAllMembersNear(BlockPos center, double radius) {
            for (Player player : getOnlineMembers()) {
                if (player.blockPosition().distSqr(center) > radius * radius) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public List<Player> getMembersNear(BlockPos center, double radius) {
            double radiusSq = radius * radius;
            return getOnlineMembers().stream()
                .filter(p -> p.blockPosition().distSqr(center) <= radiusSq)
                .collect(Collectors.toList());
        }

        @Override
        public Map<Player, BlockPos> getMemberPositions() {
            return getOnlineMembers().stream()
                .collect(Collectors.toMap(p -> p, Player::blockPosition));
        }

        @Override
        public double getMaxMemberDistance() {
            List<Player> members = getOnlineMembers();
            if (members.size() < 2) return 0;

            double maxDist = 0;
            for (int i = 0; i < members.size(); i++) {
                for (int j = i + 1; j < members.size(); j++) {
                    double dist = members.get(i).distanceToSqr(members.get(j));
                    if (dist > maxDist) maxDist = dist;
                }
            }
            return Math.sqrt(maxDist);
        }

        @Override
        public void disband() {
            disbanded = true;
            manager.remove(id);
        }

        @Override
        public boolean isDisbanded() {
            return disbanded;
        }
    }

    // ==================== TeamData Implementation ====================

    private static class TeamDataImpl implements TeamData {
        private final TeamImpl team;
        private final Map<String, Object> data = new ConcurrentHashMap<>();

        TeamDataImpl(TeamImpl team) {
            this.team = team;
        }

        void loadFromJson(JsonObject json) {
            data.clear();
            for (var entry : json.entrySet()) {
                // Simple conversion - proper impl would handle types better
                var value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    var prim = value.getAsJsonPrimitive();
                    if (prim.isBoolean()) data.put(entry.getKey(), prim.getAsBoolean());
                    else if (prim.isNumber()) data.put(entry.getKey(), prim.getAsNumber());
                    else data.put(entry.getKey(), prim.getAsString());
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
            return json;
        }

        @Override
        public void set(String key, Object value) {
            data.put(key, value);
            syncToServer();
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
            syncToServer();
        }

        @Override
        public void clear() {
            data.clear();
            syncToServer();
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
        @SuppressWarnings("unchecked")
        public <T> List<T> getList(String key, Class<T> elementType) {
            Object value = data.get(key);
            if (value instanceof List<?> list) {
                return (List<T>) list;
            }
            return new ArrayList<>();
        }

        @Override
        public long increment(String key, long delta) {
            long current = getLong(key, 0);
            long newValue = current + delta;
            data.put(key, newValue);
            syncToServer();
            return newValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> void addToList(String key, T item) {
            List<T> list = (List<T>) data.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(item);
            syncToServer();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> boolean removeFromList(String key, T item) {
            Object value = data.get(key);
            if (value instanceof List<?> list) {
                boolean removed = ((List<T>) list).remove(item);
                if (removed) syncToServer();
                return removed;
            }
            return false;
        }

        @Override
        public boolean setIfAbsent(String key, Object value) {
            if (!data.containsKey(key)) {
                data.put(key, value);
                syncToServer();
                return true;
            }
            return false;
        }

        private void syncToServer() {
            team.manager.updateTeamOnServer(team);
            team.manager.getEventBus().publish(new EiraEvents.TeamDataChangedEvent(team, null, null));
        }
    }
}
