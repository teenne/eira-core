# Eira Core API Specification
# Cross-Mod Communication & Shared Services

**Version:** 1.0 Draft  
**Mod ID:** `eira-core`  
**Package:** `org.eira.core`

---

## 1. API Entry Point

### Accessing the API

```java
import org.eira.core.api.EiraAPI;

// Get the API instance (safe to call anytime after mod init)
EiraAPI eira = EiraAPI.get();

// Access subsystems
eira.events()      // Event bus
eira.teams()       // Team management
eira.players()     // Extended player data
eira.stories()     // Story framework
eira.adventures()  // Adventure system
eira.config()      // Shared configuration
```

### API Availability Check

```java
// Check if Eira Core is present (for soft dependencies)
if (ModList.get().isLoaded("eira-core")) {
    EiraAPI eira = EiraAPI.get();
    // Use API
}

// Or use the safe accessor
EiraAPI.ifPresent(eira -> {
    // Use API safely
});
```

---

## 2. Event Bus API

### Publishing Events

```java
// Get event bus
EiraEventBus events = EiraAPI.get().events();

// Publish an event (fire-and-forget)
events.publish(new MyCustomEvent(data));

// Publish and wait for handlers
events.publishSync(new ImportantEvent(data));

// Publish with callback
events.publish(new AsyncEvent(data), result -> {
    // Called after all handlers complete
});
```

### Subscribing to Events

```java
// Method 1: Lambda subscription
events.subscribe(TeamCreatedEvent.class, event -> {
    LOGGER.info("Team created: {}", event.team().getName());
});

// Method 2: Annotation-based (register handler class)
@EiraEventHandler
public class MyEventHandlers {
    
    @Subscribe
    public void onTeamCreated(TeamCreatedEvent event) {
        // Handle event
    }
    
    @Subscribe(priority = EventPriority.HIGH)
    public void onSecretRevealed(SecretRevealedEvent event) {
        // High priority handler runs first
    }
    
    @Subscribe(async = true)
    public void onHttpReceived(HttpReceivedEvent event) {
        // Runs on async thread
    }
}

// Register handler class
events.registerHandler(new MyEventHandlers());

// Unregister when done
events.unregisterHandler(handlerInstance);
```

### Built-in Events

```java
// Team events
public record TeamCreatedEvent(Team team, @Nullable Player creator) {}
public record TeamDisbandedEvent(Team team, DisbandReason reason) {}
public record TeamMemberAddedEvent(Team team, Player player) {}
public record TeamMemberRemovedEvent(Team team, Player player, RemoveReason reason) {}
public record TeamDataChangedEvent(Team team, String key, Object oldVal, Object newVal) {}

// Story events (from Eira NPC)
public record ConversationStartedEvent(UUID npcId, Player player, String characterId) {}
public record ConversationEndedEvent(UUID npcId, Player player, int messageCount) {}
public record StorySecretRevealedEvent(UUID npcId, Player player, int level, int maxLevel) {}
public record NPCMoodChangedEvent(UUID npcId, String oldMood, String newMood) {}

// Relay events (from Eira Relay)
public record HttpReceivedEvent(String endpoint, String method, Map<String, String> params) {}
public record HttpSentEvent(String url, String method, int responseCode) {}
public record ExternalTriggerEvent(String source, String triggerId, Map<String, Object> data) {}

// Adventure events
public record AdventureStartedEvent(AdventureInstance instance, Team team) {}
public record CheckpointReachedEvent(AdventureInstance instance, Checkpoint checkpoint) {}
public record AdventureCompletedEvent(AdventureInstance instance, Duration time) {}
public record AdventureFailedEvent(AdventureInstance instance, String reason) {}

// Quest events (from Eira Quest)
public record QuestStartedEvent(Player player, String questId) {}
public record QuestObjectiveCompletedEvent(Player player, String questId, String objectiveId) {}
public record QuestCompletedEvent(Player player, String questId) {}
public record QuestAbandonedEvent(Player player, String questId) {}
```

### Custom Events

```java
// Define custom event
public record MyModCustomEvent(
    Player player,
    String action,
    Map<String, Object> data
) implements EiraEvent {
    
    // Optional: Make event cancellable
    private boolean cancelled = false;
    
    @Override
    public boolean isCancellable() { return true; }
    
    @Override
    public boolean isCancelled() { return cancelled; }
    
    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}

// Publish custom event
events.publish(new MyModCustomEvent(player, "discovered_artifact", data));
```

---

## 3. Team API

### Creating Teams

```java
TeamManager teams = EiraAPI.get().teams();

// Create team with builder
Team team = teams.create("Red Dragons")
    .withColor(ChatFormatting.RED)
    .withMaxSize(6)
    .withTag("escape_room_1")  // For filtering
    .withData("room", "lab")   // Custom data
    .build();

// Create from config
Team team = teams.createFromConfig("teams/red_team.json");
```

### Team Operations

```java
// Find teams
Optional<Team> team = teams.getById(teamId);
Optional<Team> team = teams.getByName("Red Dragons");
List<Team> allTeams = teams.getAll();
List<Team> tagged = teams.getByTag("escape_room_1");

// Player's team
Optional<Team> playerTeam = teams.getTeamOf(player);
boolean hasTeam = teams.hasTeam(player);

// Modify team
team.addMember(player);
team.removeMember(player);
team.setLeader(player);
team.rename("Blue Phoenix");
team.disband();

// Check membership
boolean isMember = team.hasMember(player);
boolean isLeader = team.isLeader(player);
List<Player> members = team.getOnlineMembers();
int size = team.getSize();
```

### Team Data

```java
TeamData data = team.getData();

// Store data
data.set("score", 150);
data.set("current_room", "library");
data.set("hints_used", 2);
data.setList("solved_puzzles", List.of("puzzle_1", "puzzle_3"));

// Retrieve data
int score = data.getInt("score", 0);
String room = data.getString("current_room", "entrance");
List<String> puzzles = data.getList("solved_puzzles", String.class);

// Atomic operations
data.increment("score", 50);      // Thread-safe increment
data.decrement("hints_used", 1);
data.addToList("solved_puzzles", "puzzle_5");

// Check data
boolean has = data.has("score");
data.remove("temporary_flag");
data.clear();
```

### Team Communication

```java
// Broadcast to team
team.broadcast("Your team found a clue!");
team.broadcast(Component.literal("Important!").withStyle(ChatFormatting.GOLD));

// Send to specific member
team.sendTo(player, "Only you can see this");

// Sound/effects to team
team.playSound(SoundEvents.NOTE_BLOCK_PLING, 1.0f, 1.0f);
team.showTitle("Chapter 2", "The Investigation Begins");

// Action bar
team.showActionBar("Time remaining: 45:00");
```

### Team Proximity

```java
// Check if team is together
boolean together = team.areAllMembersNear(centerPos, 20); // 20 block radius

// Get nearby members
List<Player> nearby = team.getMembersNear(npcPos, 10);

// Get member positions
Map<Player, BlockPos> positions = team.getMemberPositions();

// Distance between members
double maxSpread = team.getMaxMemberDistance();
```

---

## 4. Player API

### Extended Player Data

```java
PlayerManager players = EiraAPI.get().players();

// Get extended player
EiraPlayer eiraPlayer = players.get(player);
EiraPlayer eiraPlayer = players.get(playerUUID);

// Player progress
PlayerProgress progress = eiraPlayer.getProgress();
progress.set("story.haunted_mansion.chapter", "investigation");
progress.setFlag("met_the_butler", true);
progress.increment("total_secrets_found", 1);

// Check progress
String chapter = progress.getString("story.haunted_mansion.chapter", "arrival");
boolean metButler = progress.getFlag("met_the_butler");
int secrets = progress.getInt("total_secrets_found", 0);
```

### Player Tracking

```java
// Track player activity
eiraPlayer.recordActivity("talked_to_npc", Map.of("npc", "butler"));
eiraPlayer.recordLocation("visited_library");

// Get activity history
List<Activity> recent = eiraPlayer.getRecentActivities(10);
boolean visited = eiraPlayer.hasVisitedLocation("library");

// Time tracking
Duration playTime = eiraPlayer.getSessionTime();
Instant lastActive = eiraPlayer.getLastActiveTime();
```

### Cross-Session Persistence

```java
// Data persists across sessions (stored in world data)
eiraPlayer.getPersistentData().set("lifetime_score", 5000);

// Session-only data (cleared on logout)
eiraPlayer.getSessionData().set("current_hint", "check the bookshelf");
```

---

## 5. Story API

### Story Definition

```java
StoryManager stories = EiraAPI.get().stories();

// Load story from file
Story story = stories.load("haunted_mansion");

// Or define programmatically
Story story = Story.builder("haunted_mansion")
    .name("The Haunted Mansion")
    .chapter("arrival")
        .name("Arrival")
        .npc("butler")
        .npc("ghost_girl")
        .build()
    .chapter("investigation")
        .name("The Investigation")
        .requires("arrival")
        .requiresFlag("talked_to_butler")
        .npc("butler", "detective", "ghost_girl")
        .build()
    .secret("butler_secret")
        .hintInChapter("arrival")
        .revealInChapter("investigation")
        .build()
    .build();

stories.register(story);
```

### Story State

```java
// Get player's state in a story
StoryState state = stories.getState(player, "haunted_mansion");

// Chapter progress
String currentChapter = state.getCurrentChapter();
boolean canAccess = state.isChapterUnlocked("revelation");
state.advanceToChapter("investigation");

// Flags and progress
state.setFlag("found_diary", true);
state.markEvent("discovered_hidden_room");
boolean hasDiary = state.hasFlag("found_diary");

// Secret tracking
int secretLevel = state.getSecretRevealLevel("butler_secret"); // 0-3 typically
state.revealSecretHint("butler_secret"); // Increment reveal level

// Available dialogue (for NPCs to query)
List<String> availableTopics = state.getAvailableDialogueTopics("butler");
boolean canAskAbout = state.canDiscuss("butler", "the_murder");
```

### Story Events Integration

```java
// NPCs can query story state
String storyContext = stories.getContextForNPC(player, npcCharacterId);
// Returns: "Player is in chapter 'investigation', has found diary, hasn't met detective yet"

// Update story from NPC conversation
stories.handleConversationEvent(player, npcId, "discussed_murder");
```

---

## 6. Adventure API

### Adventure Definition

```java
AdventureManager adventures = EiraAPI.get().adventures();

// Load from file
Adventure adventure = adventures.load("escape_room_1");

// Or define programmatically
Adventure adventure = Adventure.builder("escape_room_1")
    .name("The Professor's Lab")
    .type(AdventureType.TIMED)
    .timeLimit(Duration.ofHours(1))
    .teamSize(2, 6)
    .maxTeams(4)
    
    .checkpoint("entrance")
        .name("Enter the Lab")
        .trigger(Trigger.areaEnter(-100, 64, -100, -80, 80, -80))
        .build()
        
    .checkpoint("first_puzzle")
        .name("Decode the Message")
        .trigger(Trigger.customEvent("puzzle_decoded"))
        .hintNpc("robot_assistant")
        .build()
        
    .checkpoint("find_key")
        .name("Find the Key")
        .trigger(Trigger.hasItem("eira:lab_key"))
        .build()
        
    .checkpoint("escape")
        .name("Escape!")
        .trigger(Trigger.areaEnter(100, 64, 100, 120, 80, 120))
        .build()
        
    .onComplete(rewards -> rewards
        .broadcast("{team} escaped in {time}!")
        .webhook("http://leaderboard/complete")
        .giveItem(Items.DIAMOND, 3))
        
    .build();

adventures.register(adventure);
```

### Running Adventures

```java
// Start adventure for a team
AdventureInstance instance = adventures.start("escape_room_1", team);

// Get active instances
List<AdventureInstance> active = adventures.getActiveInstances();
Optional<AdventureInstance> teamAdventure = adventures.getInstanceForTeam(team);

// Instance state
AdventureState state = instance.getState(); // NOT_STARTED, RUNNING, COMPLETED, FAILED
float progress = instance.getProgress(); // 0.0 - 1.0
Duration elapsed = instance.getElapsedTime();
Duration remaining = instance.getRemainingTime();

// Checkpoints
Checkpoint current = instance.getCurrentCheckpoint();
List<Checkpoint> completed = instance.getCompletedCheckpoints();
List<Checkpoint> remaining = instance.getRemainingCheckpoints();

// Manual control
instance.completeCheckpoint("first_puzzle");
instance.skipCheckpoint("optional_bonus");
instance.addTime(Duration.ofMinutes(5)); // Bonus time
instance.fail("time_expired");
instance.complete();
instance.reset();
```

### Leaderboards

```java
// Get leaderboard
Leaderboard leaderboard = adventures.getLeaderboard("escape_room_1");

// Top entries
List<LeaderboardEntry> top10 = leaderboard.getTop(10);
for (LeaderboardEntry entry : top10) {
    String teamName = entry.getTeamName();
    Duration time = entry.getCompletionTime();
    Instant when = entry.getCompletedAt();
}

// Team's rank
Optional<Integer> rank = leaderboard.getRank(team);

// Filter by date
List<LeaderboardEntry> today = leaderboard.getEntriesAfter(Instant.now().minus(1, ChronoUnit.DAYS));
```

---

## 7. Configuration API

### Shared Config

```java
EiraConfig config = EiraAPI.get().config();

// Read values
String serverName = config.getString("server.name", "Eira Server");
int maxTeams = config.getInt("teams.max", 10);
boolean debugMode = config.getBoolean("debug", false);

// Nested config
ConfigSection webhooks = config.getSection("webhooks");
String onComplete = webhooks.getString("onComplete", "");
```

### Module Registration

```java
// Register your mod's config section
config.registerSection("my_mod", defaults -> {
    defaults.set("enabled", true);
    defaults.set("feature_x", 100);
});

// Access your section
ConfigSection myConfig = config.getSection("my_mod");
```

---

## 8. Network API

### Cross-Mod Packets

```java
EiraNetwork network = EiraAPI.get().network();

// Register packet type
network.registerPacket(MyPacket.class, MyPacket::encode, MyPacket::decode, MyPacket::handle);

// Send packets
network.sendToPlayer(player, new MyPacket(data));
network.sendToTeam(team, new MyPacket(data));
network.sendToAll(new MyPacket(data));
network.sendToServer(new MyPacket(data)); // From client
```

---

## 9. Utility Classes

### Area Definitions

```java
// Define an area
Area area = Area.box(-100, 64, -100, -80, 80, -80);
Area area = Area.sphere(centerPos, 20);
Area area = Area.cylinder(centerPos, 15, 10);

// Check containment
boolean inside = area.contains(player.blockPosition());
boolean inside = area.contains(player);

// Get players in area
List<Player> inArea = area.getPlayersInside(level);
```

### Timers

```java
// Create countdown timer
Timer timer = EiraAPI.get().timers().create("escape_timer")
    .duration(Duration.ofHours(1))
    .onTick(remaining -> team.showActionBar("Time: " + formatTime(remaining)))
    .onComplete(() -> instance.fail("time_expired"))
    .onWarning(Duration.ofMinutes(5), () -> team.broadcast("5 minutes remaining!"))
    .start();

// Timer controls
timer.pause();
timer.resume();
timer.addTime(Duration.ofMinutes(5));
timer.cancel();
```

---

## 10. Usage Example: Integrating Eira NPC

```java
// In Eira NPC mod initialization
public void setupEiraIntegration() {
    EiraAPI.ifPresent(eira -> {
        // Subscribe to relay events
        eira.events().subscribe(ExternalTriggerEvent.class, event -> {
            // Physical trigger → NPC reaction
            if (event.source().equals("qr_scan")) {
                triggerNPCsNearby(event.triggerId());
            }
        });
        
        // Publish NPC events
        eira.events().subscribe(ConversationEndedEvent.class, event -> {
            // Track in player progress
            EiraPlayer player = eira.players().get(event.player());
            player.getProgress().increment("conversations_completed", 1);
        });
        
        // Use team info in NPC dialogue
        // In system prompt generation:
        Optional<Team> team = eira.teams().getTeamOf(player);
        if (team.isPresent()) {
            prompt += "The player is part of team '" + team.get().getName() + "'";
            prompt += " with " + team.get().getSize() + " members.";
        }
    });
}

// When NPC reveals a secret
public void onSecretRevealed(StorytellerNPC npc, Player player, int level) {
    EiraAPI.ifPresent(eira -> {
        // Publish event for other mods
        eira.events().publish(new StorySecretRevealedEvent(
            npc.getUUID(), player, level, npc.getCharacter().getMaxSecretLevel()
        ));
        
        // Update story state
        eira.stories().ifStoryActive(player, story -> {
            story.revealSecretHint(npc.getCharacterId() + "_secret");
        });
    });
}
```

---

## 11. Maven/Gradle Dependency

```groovy
// build.gradle.kts
repositories {
    maven {
        name = "Eira"
        url = uri("https://maven.eira.org/releases")
    }
}

dependencies {
    // Required dependency
    implementation("org.eira:eira-core:1.0.0")
    
    // Or optional (soft dependency)
    compileOnly("org.eira:eira-core:1.0.0")
}
```

```toml
# neoforge.mods.toml
[[dependencies.my_mod]]
    modId = "eira-core"
    type = "required"  # or "optional"
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```
