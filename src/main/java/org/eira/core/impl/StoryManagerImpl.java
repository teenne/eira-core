package org.eira.core.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.player.Player;
import org.eira.core.EiraCore;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.event.EiraEvents;
import org.eira.core.api.story.Story;
import org.eira.core.api.story.StoryManager;
import org.eira.core.api.story.StoryState;
import org.eira.core.api.team.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Story manager implementation that uses the external API server.
 *
 * <p>Story definitions and player states are stored on the API server.
 */
public class StoryManagerImpl implements StoryManager {

    private final EiraEventBus eventBus;
    private final ApiClient apiClient;

    // Local cache of stories
    private final Map<String, StoryImpl> storyCache = new ConcurrentHashMap<>();

    // Cache of story states (playerId/teamId + storyId -> state)
    private final Map<String, StoryStateImpl> stateCache = new ConcurrentHashMap<>();

    public StoryManagerImpl(EiraEventBus eventBus, ApiClient apiClient) {
        this.eventBus = eventBus;
        this.apiClient = apiClient;
    }

    // ==================== StoryManager Interface ====================

    @Override
    public void register(Story story) {
        if (story instanceof StoryImpl impl) {
            storyCache.put(story.getId(), impl);
        }
    }

    @Override
    public Optional<Story> get(String storyId) {
        return Optional.ofNullable(storyCache.get(storyId));
    }

    @Override
    public List<Story> getRegisteredStories() {
        return new ArrayList<>(storyCache.values());
    }

    @Override
    public void loadStories() {
        apiClient.get("/stories", JsonArray.class)
            .thenAccept(response -> {
                if (response.isSuccess() && response.getData() != null) {
                    storyCache.clear();

                    for (var elem : response.getData()) {
                        JsonObject json = elem.getAsJsonObject();
                        StoryImpl story = StoryImpl.fromJson(json);
                        storyCache.put(story.getId(), story);
                    }

                    EiraCore.LOGGER.info("Loaded {} stories from server", storyCache.size());
                }
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to load stories: {}", ex.getMessage());
                return null;
            });
    }

    @Override
    public StoryState getState(Player player, String storyId) {
        String cacheKey = "player:" + player.getUUID() + ":" + storyId;
        return stateCache.computeIfAbsent(cacheKey, key -> {
            StoryStateImpl state = fetchOrCreateState(player.getUUID().toString(), storyId, false);
            return state;
        });
    }

    @Override
    public StoryState getState(Team team, String storyId) {
        String cacheKey = "team:" + team.getId() + ":" + storyId;
        return stateCache.computeIfAbsent(cacheKey, key -> {
            StoryStateImpl state = fetchOrCreateState(team.getId().toString(), storyId, true);
            return state;
        });
    }

    private StoryStateImpl fetchOrCreateState(String entityId, String storyId, boolean isTeam) {
        try {
            String endpoint = isTeam ?
                "/stories/" + storyId + "/state/team/" + entityId :
                "/stories/" + storyId + "/state/player/" + entityId;

            var response = apiClient.get(endpoint, JsonObject.class).get();
            if (response.isSuccess() && response.getData() != null) {
                return StoryStateImpl.fromJson(response.getData(), storyId, this);
            }
        } catch (Exception e) {
            EiraCore.LOGGER.debug("Creating new story state for {} in {}", entityId, storyId);
        }

        // Create new state
        StoryImpl story = storyCache.get(storyId);
        String firstChapter = story != null && !story.getChapters().isEmpty() ?
            story.getChapters().get(0).getId() : null;

        return new StoryStateImpl(storyId, firstChapter, this);
    }

    @Override
    public String getContextForNPC(Player player, String npcCharacterId) {
        StringBuilder context = new StringBuilder();

        for (StoryImpl story : storyCache.values()) {
            StoryState state = getState(player, story.getId());

            // Add current chapter context
            String currentChapter = state.getCurrentChapter();
            if (currentChapter != null) {
                Story.Chapter chapter = story.getChapter(currentChapter);
                if (chapter != null && chapter.getNpcIds().contains(npcCharacterId)) {
                    context.append("Currently in chapter: ").append(chapter.getName()).append("\n");
                }
            }

            // Add revealed secrets
            for (Story.Secret secret : story.getSecrets()) {
                int revealLevel = state.getSecretRevealLevel(secret.getId());
                if (revealLevel > 0) {
                    context.append("Secret '").append(secret.getId())
                        .append("' revealed at level ").append(revealLevel)
                        .append("/").append(secret.getMaxRevealLevel()).append("\n");
                }
            }

            // Add available dialogue topics
            List<String> topics = state.getAvailableDialogueTopics(npcCharacterId);
            if (!topics.isEmpty()) {
                context.append("Available topics: ").append(String.join(", ", topics)).append("\n");
            }
        }

        return context.toString();
    }

    @Override
    public void handleConversationEvent(Player player, String npcId, String eventType) {
        // Forward to API
        JsonObject payload = new JsonObject();
        payload.addProperty("playerId", player.getUUID().toString());
        payload.addProperty("npcId", npcId);
        payload.addProperty("eventType", eventType);

        apiClient.post("/events/conversation", payload, Void.class);
    }

    // ==================== Internal Methods ====================

    void updateStateOnServer(StoryStateImpl state, String entityId, boolean isTeam) {
        String endpoint = isTeam ?
            "/stories/" + state.getStoryId() + "/state/team/" + entityId :
            "/stories/" + state.getStoryId() + "/state/player/" + entityId;

        apiClient.put(endpoint, state.toJson(), Void.class);
    }

    EiraEventBus getEventBus() {
        return eventBus;
    }

    // ==================== Story Implementation ====================

    public static class StoryImpl implements Story {
        private final String id;
        private final String name;
        private final List<Chapter> chapters = new ArrayList<>();
        private final List<Secret> secrets = new ArrayList<>();

        public StoryImpl(String id, String name) {
            this.id = id;
            this.name = name;
        }

        static StoryImpl fromJson(JsonObject json) {
            String id = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString() : id;

            StoryImpl story = new StoryImpl(id, name);

            if (json.has("chapters") && json.get("chapters").isJsonArray()) {
                for (var elem : json.getAsJsonArray("chapters")) {
                    story.chapters.add(ChapterImpl.fromJson(elem.getAsJsonObject()));
                }
            }

            if (json.has("secrets") && json.get("secrets").isJsonArray()) {
                for (var elem : json.getAsJsonArray("secrets")) {
                    story.secrets.add(SecretImpl.fromJson(elem.getAsJsonObject()));
                }
            }

            return story;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public List<Chapter> getChapters() { return new ArrayList<>(chapters); }
        @Override public List<Secret> getSecrets() { return new ArrayList<>(secrets); }

        @Override
        public Chapter getChapter(String chapterId) {
            return chapters.stream()
                .filter(c -> c.getId().equals(chapterId))
                .findFirst()
                .orElse(null);
        }
    }

    public static class ChapterImpl implements Story.Chapter {
        private final String id;
        private final String name;
        private final List<String> npcIds;
        private final List<String> requirements;

        public ChapterImpl(String id, String name, List<String> npcIds, List<String> requirements) {
            this.id = id;
            this.name = name;
            this.npcIds = npcIds;
            this.requirements = requirements;
        }

        static ChapterImpl fromJson(JsonObject json) {
            String id = json.get("id").getAsString();
            String name = json.has("name") ? json.get("name").getAsString() : id;

            List<String> npcIds = new ArrayList<>();
            if (json.has("npcIds")) {
                for (var elem : json.getAsJsonArray("npcIds")) {
                    npcIds.add(elem.getAsString());
                }
            }

            List<String> requirements = new ArrayList<>();
            if (json.has("requirements")) {
                for (var elem : json.getAsJsonArray("requirements")) {
                    requirements.add(elem.getAsString());
                }
            }

            return new ChapterImpl(id, name, npcIds, requirements);
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public List<String> getNpcIds() { return npcIds; }
        @Override public List<String> getRequirements() { return requirements; }

        @Override
        public boolean isUnlockedBy(StoryState state) {
            for (String req : requirements) {
                if (!state.hasEvent(req) && !state.hasFlag(req)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class SecretImpl implements Story.Secret {
        private final String id;
        private final int maxRevealLevel;
        private final List<String> hintChapters;
        private final String revealChapter;

        public SecretImpl(String id, int maxRevealLevel, List<String> hintChapters, String revealChapter) {
            this.id = id;
            this.maxRevealLevel = maxRevealLevel;
            this.hintChapters = hintChapters;
            this.revealChapter = revealChapter;
        }

        static SecretImpl fromJson(JsonObject json) {
            String id = json.get("id").getAsString();
            int maxRevealLevel = json.has("maxRevealLevel") ? json.get("maxRevealLevel").getAsInt() : 3;
            String revealChapter = json.has("revealChapter") ? json.get("revealChapter").getAsString() : null;

            List<String> hintChapters = new ArrayList<>();
            if (json.has("hintChapters")) {
                for (var elem : json.getAsJsonArray("hintChapters")) {
                    hintChapters.add(elem.getAsString());
                }
            }

            return new SecretImpl(id, maxRevealLevel, hintChapters, revealChapter);
        }

        @Override public String getId() { return id; }
        @Override public int getMaxRevealLevel() { return maxRevealLevel; }
        @Override public List<String> getHintChapters() { return hintChapters; }
        @Override public String getRevealChapter() { return revealChapter; }
    }

    // ==================== StoryState Implementation ====================

    public static class StoryStateImpl implements StoryState {
        private final String storyId;
        private final StoryManagerImpl manager;

        private String currentChapter;
        private final List<String> unlockedChapters = new ArrayList<>();
        private final Map<String, Boolean> flags = new HashMap<>();
        private final Set<String> events = new HashSet<>();
        private final Map<String, Integer> secretLevels = new HashMap<>();
        private final Map<String, List<String>> dialogueTopics = new HashMap<>();

        public StoryStateImpl(String storyId, String initialChapter, StoryManagerImpl manager) {
            this.storyId = storyId;
            this.currentChapter = initialChapter;
            this.manager = manager;

            if (initialChapter != null) {
                unlockedChapters.add(initialChapter);
            }
        }

        static StoryStateImpl fromJson(JsonObject json, String storyId, StoryManagerImpl manager) {
            String currentChapter = json.has("currentChapter") ?
                json.get("currentChapter").getAsString() : null;

            StoryStateImpl state = new StoryStateImpl(storyId, null, manager);
            state.currentChapter = currentChapter;

            if (json.has("unlockedChapters")) {
                for (var elem : json.getAsJsonArray("unlockedChapters")) {
                    state.unlockedChapters.add(elem.getAsString());
                }
            }

            if (json.has("flags") && json.get("flags").isJsonObject()) {
                for (var entry : json.getAsJsonObject("flags").entrySet()) {
                    state.flags.put(entry.getKey(), entry.getValue().getAsBoolean());
                }
            }

            if (json.has("events")) {
                for (var elem : json.getAsJsonArray("events")) {
                    state.events.add(elem.getAsString());
                }
            }

            if (json.has("secretLevels") && json.get("secretLevels").isJsonObject()) {
                for (var entry : json.getAsJsonObject("secretLevels").entrySet()) {
                    state.secretLevels.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }

            return state;
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            if (currentChapter != null) {
                json.addProperty("currentChapter", currentChapter);
            }

            JsonArray unlocked = new JsonArray();
            unlockedChapters.forEach(unlocked::add);
            json.add("unlockedChapters", unlocked);

            JsonObject flagsObj = new JsonObject();
            flags.forEach(flagsObj::addProperty);
            json.add("flags", flagsObj);

            JsonArray eventsArr = new JsonArray();
            events.forEach(eventsArr::add);
            json.add("events", eventsArr);

            JsonObject secrets = new JsonObject();
            secretLevels.forEach(secrets::addProperty);
            json.add("secretLevels", secrets);

            return json;
        }

        @Override public String getStoryId() { return storyId; }
        @Override public String getCurrentChapter() { return currentChapter; }

        @Override
        public void advanceToChapter(String chapterId) {
            String previousChapter = currentChapter;
            currentChapter = chapterId;
            if (!unlockedChapters.contains(chapterId)) {
                unlockedChapters.add(chapterId);
            }
            // Note: Would need entity reference to sync to server
            manager.getEventBus().publish(new EiraEvents.ChapterUnlockedEvent(storyId, chapterId, null));
        }

        @Override
        public boolean isChapterUnlocked(String chapterId) {
            return unlockedChapters.contains(chapterId);
        }

        @Override
        public List<String> getUnlockedChapters() {
            return new ArrayList<>(unlockedChapters);
        }

        @Override
        public void setFlag(String flag, boolean value) {
            flags.put(flag, value);
        }

        @Override
        public boolean hasFlag(String flag) {
            return flags.getOrDefault(flag, false);
        }

        @Override
        public void markEvent(String eventId) {
            events.add(eventId);
        }

        @Override
        public boolean hasEvent(String eventId) {
            return events.contains(eventId);
        }

        @Override
        public int getSecretRevealLevel(String secretId) {
            return secretLevels.getOrDefault(secretId, 0);
        }

        @Override
        public void revealSecretHint(String secretId) {
            int current = secretLevels.getOrDefault(secretId, 0);
            secretLevels.put(secretId, current + 1);
            manager.getEventBus().publish(new EiraEvents.SecretRevealedEvent(storyId, secretId, current + 1, null));
        }

        @Override
        public List<String> getAvailableDialogueTopics(String npcId) {
            return dialogueTopics.getOrDefault(npcId, Collections.emptyList());
        }

        @Override
        public boolean canDiscuss(String npcId, String topic) {
            List<String> topics = dialogueTopics.get(npcId);
            return topics != null && topics.contains(topic);
        }
    }
}
