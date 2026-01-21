# Eira Mod Ecosystem
# Architecture & Vision

**Version:** 1.0
**Organization:** Eira (Non-profit coding education)
**Target:** Minecraft 1.21.4 with NeoForge 21.4.x

---

## Current Implementation Status

| Component | Status | Repository |
|-----------|--------|------------|
| **Eira Core** (Minecraft mod) | ✅ Beta | [eira-core](https://github.com/teenne/eira-core) |
| **Eira API** (Backend server) | ✅ Beta | [eira-api](https://github.com/teenne/eira-api) |
| **Eira Relay** | 🔶 Planned | - |
| **Eira NPC** | 🔶 Planned | - |
| **Eira Quest** | 🔶 Planned | - |

### Eira Core Features (Complete)
- ✅ HTTP client for REST API calls
- ✅ WebSocket client for real-time communication
- ✅ Event bus for cross-mod communication
- ✅ Team, Player, Adventure, Story managers
- ✅ NeoForge event bridge (forwards Minecraft events)
- ✅ Instruction handler (executes server commands)
- ✅ Admin commands (`/eira`)
- ✅ Eira Relay compatibility layer

### Eira API Features (Complete)
- ✅ Teams API (CRUD, members)
- ✅ Players API (progress, data)
- ✅ Adventures API (checkpoints, leaderboards)
- ✅ Stories API (chapters, flags, secrets)
- ✅ Events API (ingestion, triggers, instructions)
- ✅ WebSocket handler (real-time bidirectional)
- ✅ Health monitoring (DB latency, WS stats)
- ✅ 71 integration tests

---

## Vision

Create an interconnected Minecraft mod ecosystem that enables **immersive educational experiences** bridging physical and digital worlds. Perfect for:

- Coding workshops and hackathons
- Museum/science center installations
- Escape room experiences
- Team-based learning adventures
- STEM education programs

---

## Ecosystem Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EIRA MOD ECOSYSTEM                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         EIRA CORE (API)                              │    │
│  │  • Cross-mod event bus                                               │    │
│  │  • Team & player management                                          │    │
│  │  • Story/adventure framework                                         │    │
│  │  • Shared utilities & config                                         │    │
│  │  • Inter-mod communication API                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│         ▲                    ▲                    ▲                          │
│         │                    │                    │                          │
│    ┌────┴────┐          ┌────┴────┐          ┌────┴────┐                    │
│    │         │          │         │          │         │                    │
│  ┌─┴───────────┐    ┌───┴─────────┐    ┌─────┴───────┐                     │
│  │ EIRA RELAY  │    │  EIRA NPC   │    │ EIRA QUEST  │   (future)          │
│  │             │    │(Storyteller)│    │             │                     │
│  │ • HTTP In   │◄──►│ • AI NPCs   │◄──►│ • Objectives│                     │
│  │ • HTTP Out  │    │ • Stories   │    │ • Progress  │                     │
│  │ • Webhooks  │    │ • Dialogue  │    │ • Rewards   │                     │
│  └─────────────┘    └─────────────┘    └─────────────┘                     │
│         ▲                    ▲                    ▲                          │
│         │                    │                    │                          │
│         └────────────────────┴────────────────────┘                          │
│                              │                                               │
│                    ┌─────────┴─────────┐                                    │
│                    │   EXTERNAL WORLD   │                                    │
│                    │  • IoT devices     │                                    │
│                    │  • Web dashboards  │                                    │
│                    │  • Mobile apps     │                                    │
│                    │  • QR codes        │                                    │
│                    └───────────────────┘                                    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Breakdown

### 1. Eira Core (Foundation)

**Purpose:** Shared infrastructure for all Eira mods

| Component | Description |
|-----------|-------------|
| **Event Bus** | Cross-mod event system for loose coupling |
| **Team API** | Create, manage, track teams of players |
| **Player API** | Extended player data, progress tracking |
| **Story API** | Framework for narrative experiences |
| **Adventure API** | Objectives, checkpoints, completion tracking |
| **Config API** | Shared configuration management |
| **Network API** | Common packet infrastructure |

### 2. Eira Relay (Existing)

**Purpose:** HTTP communication bridge

| Feature | Description |
|---------|-------------|
| HTTP Receiver | Redstone on incoming webhooks |
| HTTP Sender | HTTP requests on redstone |
| Webhook Server | Built-in HTTP server |

**New with Core:** Publishes events to Eira Event Bus

### 3. Eira NPC / Storyteller (This Project)

**Purpose:** AI-powered narrative characters

| Feature | Description |
|---------|-------------|
| AI NPCs | LLM-powered conversations |
| Characters | Configurable personalities |
| World Awareness | Context-aware responses |
| Secrets | Hidden agendas and reveals |

**New with Core:** Uses Team API, Story API, publishes story events

### 4. Eira Quest (Future)

**Purpose:** Structured objectives and progression

| Feature | Description |
|---------|-------------|
| Quest Definitions | YAML/JSON quest configs |
| Objectives | Tasks, items, locations, kills |
| Progress Tracking | Per-player and per-team |
| Rewards | Items, effects, unlocks |
| Branching | Conditional quest paths |

---

## Cross-Mod Communication

### Event Bus Architecture

```java
// Any mod can publish events
EiraCore.events().publish(new StorySecretRevealedEvent(npc, player, secretLevel));

// Any mod can subscribe
EiraCore.events().subscribe(StorySecretRevealedEvent.class, event -> {
    // React to secret being revealed
    // Maybe unlock a quest, trigger physical effect, etc.
});
```

### Event Categories

| Category | Events |
|----------|--------|
| **Team** | TeamCreated, TeamJoined, TeamLeft, TeamDisbanded |
| **Story** | ConversationStarted, SecretRevealed, MoodChanged |
| **Quest** | QuestStarted, ObjectiveCompleted, QuestCompleted |
| **Relay** | HttpReceived, HttpSent, ExternalTrigger |
| **Adventure** | CheckpointReached, AreaEntered, TimerExpired |

### Example Flow: Team Escape Room

```
1. Physical QR scan
   └─► Eira Relay receives HTTP
       └─► Publishes: ExternalTriggerEvent("qr_entrance")
           └─► Eira Quest listens: Starts "Escape Room" quest for team
           └─► Eira NPC listens: Guardian NPC greets team
           
2. Team talks to NPC
   └─► Eira NPC: Player asks about puzzle
       └─► Publishes: ConversationEvent(topic="puzzle_hint")
           └─► Eira Quest listens: Marks "talked to guardian" objective
           
3. Team solves puzzle (redstone)
   └─► Eira Quest: Detects puzzle solved
       └─► Publishes: ObjectiveCompletedEvent("first_puzzle")
           └─► Eira NPC listens: NPCs react with congratulations
           └─► Eira Relay listens: Sends webhook to unlock physical door
```

---

## Team Management

### Team API Features

```java
// Create a team
Team team = EiraCore.teams().create("Red Dragons")
    .withColor(ChatFormatting.RED)
    .withMaxSize(4)
    .build();

// Add players
team.addMember(player);
team.setLeader(player);

// Track team progress
team.getData().set("puzzles_solved", 3);
team.getData().increment("points", 100);

// Team-wide effects
team.broadcast("Your team found a clue!");
team.grantAdvancement("eira:first_puzzle");

// Team proximity
boolean together = team.areAllMembersNear(position, 10);
List<Player> nearby = team.getMembersNear(position, 50);
```

### Team Events

```java
public record TeamCreatedEvent(Team team, Player creator) {}
public record TeamMemberJoinedEvent(Team team, Player player) {}
public record TeamMemberLeftEvent(Team team, Player player, LeaveReason reason) {}
public record TeamProgressEvent(Team team, String key, Object oldValue, Object newValue) {}
public record TeamCompletedObjectiveEvent(Team team, String objectiveId) {}
```

---

## Story API

### Story Structure

```yaml
# stories/haunted_mansion.yml
story:
  id: haunted_mansion
  name: "The Haunted Mansion Mystery"
  
  chapters:
    - id: arrival
      name: "Arrival"
      npcs: [butler, ghost_girl]
      unlocks_after: null
      
    - id: investigation
      name: "The Investigation"
      npcs: [butler, detective, ghost_girl]
      unlocks_after: arrival
      requirements:
        - talked_to_butler
        
    - id: revelation
      name: "The Truth Revealed"
      npcs: [ghost_girl, villain]
      unlocks_after: investigation
      requirements:
        - found_diary
        - solved_music_puzzle
        
  secrets:
    - id: butler_secret
      reveal_chapter: investigation
      hint_chapters: [arrival]
      
  endings:
    - id: good_ending
      conditions:
        - saved_ghost
        - exposed_villain
    - id: bad_ending
      conditions:
        - villain_escaped
```

### Story API Usage

```java
// Get current story state
StoryState state = EiraCore.stories().getState(player, "haunted_mansion");

// Check chapter availability
boolean canAccess = state.isChapterUnlocked("revelation");

// Progress tracking
state.markEvent("found_diary");
state.setFlag("talked_to_butler", true);

// Get available NPC dialogue options based on story state
List<DialogueOption> options = state.getAvailableDialogue(npc);
```

---

## Adventure API

### Adventure Types

| Type | Description | Use Case |
|------|-------------|----------|
| **Linear** | Sequential checkpoints | Guided tour, tutorial |
| **Open** | Any order completion | Exploration, collection |
| **Timed** | Time limit | Escape room, race |
| **Competitive** | Team vs team | Capture the flag, quiz |
| **Cooperative** | Team together | Puzzles, boss fights |

### Adventure Definition

```yaml
# adventures/escape_room_1.yml
adventure:
  id: escape_room_1
  name: "The Professor's Lab"
  type: timed
  time_limit: 3600  # 1 hour in seconds
  
  teams:
    min: 1
    max: 4
    size: 2-6
    
  checkpoints:
    - id: entrance
      name: "Enter the Lab"
      trigger: area_enter
      area: [-100, 64, -100, -80, 80, -80]
      
    - id: first_puzzle
      name: "Decode the Message"
      trigger: custom
      event: "puzzle_decoded"
      hint_npc: assistant_robot
      
    - id: find_key
      name: "Find the Key"  
      trigger: has_item
      item: "eira:lab_key"
      
    - id: escape
      name: "Escape!"
      trigger: area_enter
      area: [100, 64, 100, 120, 80, 120]
      
  rewards:
    completion:
      - type: broadcast
        message: "Team {team} escaped in {time}!"
      - type: webhook
        url: "http://leaderboard/complete"
    
  leaderboard:
    track: completion_time
    display: fastest
```

### Adventure API Usage

```java
// Start adventure for team
Adventure adventure = EiraCore.adventures().get("escape_room_1");
AdventureInstance instance = adventure.start(team);

// Check progress
float progress = instance.getProgress(); // 0.0 - 1.0
List<Checkpoint> completed = instance.getCompletedCheckpoints();
Checkpoint current = instance.getCurrentCheckpoint();

// Manual checkpoint completion
instance.completeCheckpoint("first_puzzle");

// Time tracking
Duration elapsed = instance.getElapsedTime();
Duration remaining = instance.getRemainingTime();

// End states
instance.complete(); // Success
instance.fail("time_expired"); // Failure
instance.abandon(); // Cancelled
```

---

## External Integration

### Dashboard API

Eira Core exposes REST endpoints for external dashboards:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/teams` | GET | List all teams |
| `/api/teams/{id}` | GET | Team details & progress |
| `/api/adventures/active` | GET | Active adventure instances |
| `/api/leaderboard/{adventure}` | GET | Adventure leaderboard |
| `/api/players/{uuid}/progress` | GET | Player's story/quest progress |

### Webhook Callbacks

Configure callbacks for external systems:

```toml
[eira.webhooks]
    onTeamComplete = "http://dashboard/team-complete"
    onLeaderboardUpdate = "http://display/leaderboard"
    onAllTeamsFinished = "http://lights/celebration"
```

### WebSocket Stream (Future)

Real-time event stream for live dashboards:

```javascript
const ws = new WebSocket('ws://minecraft-server:8081/eira/events');
ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    // Update dashboard in real-time
    updateTeamProgress(data.team, data.progress);
};
```

---

## File Structure

```
eira-core/
├── src/main/java/org/eira/core/
│   ├── EiraCore.java              # Main mod, API entry point
│   ├── api/
│   │   ├── EiraAPI.java           # Public API interface
│   │   ├── event/                 # Event system
│   │   │   ├── EiraEventBus.java
│   │   │   └── events/            # Event classes
│   │   ├── team/                  # Team management
│   │   │   ├── Team.java
│   │   │   ├── TeamManager.java
│   │   │   └── TeamData.java
│   │   ├── story/                 # Story framework
│   │   │   ├── Story.java
│   │   │   ├── StoryState.java
│   │   │   └── Chapter.java
│   │   ├── adventure/             # Adventure framework
│   │   │   ├── Adventure.java
│   │   │   ├── AdventureInstance.java
│   │   │   └── Checkpoint.java
│   │   └── player/                # Extended player data
│   │       ├── EiraPlayer.java
│   │       └── PlayerProgress.java
│   ├── impl/                      # Internal implementations
│   ├── network/                   # Shared packets
│   ├── config/                    # Configuration
│   └── web/                       # REST API server
│
├── src/main/resources/
│   ├── META-INF/neoforge.mods.toml
│   └── data/eira/                 # Default data
│
└── docs/
    ├── API.md
    ├── EVENTS.md
    └── INTEGRATION.md
```

---

## Mod Dependencies

```
┌─────────────┐
│ Eira Core   │  ◄── Required by all Eira mods
└──────┬──────┘
       │
       ├──────────────────┬──────────────────┐
       │                  │                  │
       ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ Eira Relay  │    │  Eira NPC   │    │ Eira Quest  │
│ (optional)  │    │ (optional)  │    │ (optional)  │
└─────────────┘    └─────────────┘    └─────────────┘
```

**Dependency Rules:**
- Eira Core has no dependencies (except NeoForge)
- All other Eira mods require Eira Core
- Eira mods are independent of each other (soft dependencies)
- Mods enhance each other when present together

---

## Implementation Priority

### Phase 1: Eira Core Foundation
1. Event bus system
2. Basic team management
3. Player data API
4. Configuration framework

### Phase 2: Module Integration
1. Update Eira Relay to use Core events
2. Update Eira NPC to use Core APIs
3. Cross-mod event handling

### Phase 3: Story & Adventure
1. Story framework
2. Adventure system
3. Progress tracking
4. Leaderboards

### Phase 4: External Integration
1. REST API
2. Dashboard support
3. WebSocket streaming
4. Mobile app integration

---

## Educational Use Cases

### Coding Workshop
- Teams compete to solve coding puzzles
- NPCs give hints based on progress
- Physical buttons submit answers
- Leaderboard on external display

### Museum Installation
- Visitors scan QR codes at exhibits
- NPCs tell stories about artifacts
- Progress tracked across visit
- Completion unlocks gift shop discount

### Escape Room
- Teams work together in Minecraft
- Physical and digital puzzles interlink
- Time pressure with live countdown
- Physical doors unlock on completion

### STEM Challenge
- Science experiments trigger Minecraft events
- NPCs explain concepts
- Teams earn points for discoveries
- Results feed into real grading system

---

## Next Steps

### Completed
- ✅ Review architecture - Scope and priorities defined
- ✅ Create Eira Core mod - Foundation implementation complete
- ✅ Create Eira API server - Backend implementation complete
- ✅ Define event schemas - Standardized in Zod schemas

### In Progress
- 🔶 Build example adventure - Showcase the ecosystem
- 🔶 Production deployment guide
- 🔶 API documentation (OpenAPI/Swagger)

### Planned
1. **Update Eira Relay** - Integrate with Core events
2. **Create Eira NPC** - AI-powered storytelling NPCs
3. **Create Eira Quest** - Structured objectives system
4. **Admin dashboard** - Web UI for management
5. **Mobile app** - Companion app for participants

### Development Resources
- [Eira Core README](../README.md)
- [Eira API README](https://github.com/teenne/eira-api#readme)
- [API Roadmap](https://github.com/teenne/eira-api#roadmap)
