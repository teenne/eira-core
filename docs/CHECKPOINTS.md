# Eira Checkpoint System
## Event-Driven Adventure Progress

The checkpoint system enables creating complex, event-driven adventures where progress can be triggered by:
- **Redstone events** (from Eira Relay blocks)
- **NPC interactions** (from Eira NPC conversations, secrets, clues)
- **HTTP requests** (QR codes, sensors, external APIs)
- **Game events** (entering areas, obtaining items, etc.)

---

## How It Works

```
┌──────────────────────────────────────────────────────────────────────┐
│                         EVENT FLOW                                    │
│                                                                       │
│   Physical World          Minecraft              Adventure System    │
│   ─────────────          ─────────              ────────────────     │
│                                                                       │
│   [QR Code] ──HTTP──► [Eira Relay] ──event──► [CheckpointTracker]    │
│                           │                          │                │
│   [Sensor] ──HTTP──► [Eira Relay]                    │                │
│                           │                          │                │
│   [Button] ──wire──► [Redstone] ──event──────────────┤                │
│                                                      │                │
│                    [Player talks] ──► [Eira NPC] ───event───┤        │
│                                           │                  │        │
│                    [Secret revealed] ─────┘                  ▼        │
│                                                     ┌────────────┐    │
│                                                     │ Checkpoint │    │
│                                                     │ Completed! │    │
│                                                     └─────┬──────┘    │
│                                                           │           │
│                                                           ▼           │
│                                                   [Unlock next]       │
│                                                   [Execute actions]   │
│                                                   [Update score]      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Checkpoint States

```
         ┌─────────────────────────────────────────┐
         │                                         │
         ▼                                         │
    ┌─────────┐     prerequisites     ┌───────────┴───┐
    │ LOCKED  │ ─────── met ────────► │   AVAILABLE   │
    └─────────┘                       └───────┬───────┘
                                              │
                              partial match   │   full match
                              (composite)     │   (or simple)
                                    ┌─────────┴─────────┐
                                    ▼                   ▼
                            ┌─────────────┐     ┌───────────┐
                            │ IN_PROGRESS │────►│ COMPLETED │
                            └─────────────┘     └─────┬─────┘
                                                      │
                                                      ▼
                                              [Unlocks dependents]
```

---

## Trigger Types

### 1. Redstone Triggers

From Eira Relay HTTP Receiver blocks:

```java
// Trigger when redstone activates at specific location
CheckpointTrigger.redstone()
    .onSignal()
    .at(100, 64, 200)
    .withMinStrength(10)
    .build();

// Trigger on any redstone near a block type
CheckpointTrigger.redstone()
    .onPulse()
    .nearBlock("minecraft:lever", 5)
    .build();
```

**Use cases:**
- Physical button connected to Relay triggers checkpoint
- Secret redstone circuit completes puzzle
- Timer-based redstone patterns

### 2. NPC Triggers

From Eira NPC conversations:

```java
// Trigger when player talks to specific NPC
CheckpointTrigger.npc()
    .onConversationWith("oracle")
    .withMinMessages(3)
    .build();

// Trigger when NPC reveals a secret
CheckpointTrigger.npc()
    .onSecretRevealed("professor", 2)  // Level 2 secret
    .build();

// Trigger when specific topic discussed
CheckpointTrigger.npc()
    .onConversationWith("guard")
    .aboutTopic("the_key")
    .build();
```

**Use cases:**
- "Talk to the guide" objective
- "Learn the secret" objective
- Conversation-gated progress

### 3. HTTP Triggers

From QR codes, sensors, external APIs:

```java
// Trigger on specific endpoint
CheckpointTrigger.http()
    .onEndpoint("/puzzle/solved")
    .build();

// Trigger with required parameters
CheckpointTrigger.http()
    .onEndpoint("/qr/scan")
    .withParam("code", "SECRET123")
    .build();

// Trigger from specific source
CheckpointTrigger.http()
    .onEndpoint("/sensor/motion")
    .withHeader("X-Location", "entrance")
    .build();
```

**Use cases:**
- QR code scanning unlocks checkpoint
- Motion sensor triggers event
- External system integration

### 4. Game Triggers

Standard Minecraft interactions:

```java
// Enter an area
CheckpointTrigger.game()
    .onEnterArea(-100, 64, -100, -80, 80, -80)
    .build();

// Obtain item
CheckpointTrigger.game()
    .onObtainItem("eira:key")
    .build();

// Kill entity
CheckpointTrigger.game()
    .onKillEntity("minecraft:zombie")
    .build();
```

### 5. Progress Triggers

Based on accumulated progress:

```java
// When progress value reached
CheckpointTrigger.progress()
    .onValueReached("puzzles_solved", 3)
    .build();

// When flag set
CheckpointTrigger.progress()
    .onFlag("found_secret_room")
    .build();

// When story chapter reached
CheckpointTrigger.progress()
    .onStoryChapter("haunted_mansion", "chapter3")
    .build();
```

---

## Composite Triggers

### ALL_OF - Require Multiple Conditions

```java
// Must talk to NPC AND have the key
CheckpointTrigger.all(
    CheckpointTrigger.npc().onConversationWith("guard").build(),
    CheckpointTrigger.game().onObtainItem("eira:key").build()
);
```

Progress is tracked - completing one condition puts checkpoint IN_PROGRESS.

### ANY_OF - Alternative Paths

```java
// Can be solved by QR code OR redstone puzzle
CheckpointTrigger.any(
    CheckpointTrigger.http().onEndpoint("/puzzle/qr").build(),
    CheckpointTrigger.redstone().at(50, 65, 50).build()
);
```

### SEQUENCE - Ordered Steps

```java
// Must do steps in order
CheckpointTrigger.sequence(
    CheckpointTrigger.game().onObtainItem("eira:keycard").build(),
    CheckpointTrigger.redstone().at(60, 65, 0).build(),  // Insert card
    CheckpointTrigger.game().onEnterArea(100, 64, 0, 120, 80, 20).build()  // Enter door
);
```

---

## Checkpoint Dependencies

Checkpoints can require prerequisites and unlock others:

```
┌─────────────────────────────────────────────────────────────────┐
│                    ADVENTURE FLOW EXAMPLE                        │
│                                                                  │
│   [Start] ──────────────────┬────────────────────► [Explore]    │
│      │                      │                          │         │
│      │                      │                          ▼         │
│      │                      │                    [Find Tools]    │
│      ▼                      │                          │         │
│   [Find Guide] ─────────────┤                          │         │
│      │                      │                          ▼         │
│      ▼                      │                   [Bonus Room]     │
│   [Get Clue] ───────────────┤                     (optional)     │
│      │                      │                                    │
│      ▼                      │                                    │
│   [Solve Puzzle] ◄──────────┘                                    │
│      │        ▲                                                  │
│      │        └── (QR code OR redstone)                         │
│      ▼                                                           │
│   [Find Key] ──────────────────────────────────► [Escape!]      │
└─────────────────────────────────────────────────────────────────┘
```

**Definition:**

```java
Adventure adventure = AdventureBuilder.create("escape_room")
    .name("Escape Room")
    
    .checkpoint("start")
        .name("Enter")
        .triggeredByArea(...)
        .unlocks("find_guide", "explore")
        .build()
    
    .checkpoint("find_guide")
        .name("Find the Guide")
        .triggeredByNPC("guide")
        .requires("start")
        .unlocks("get_clue")
        .build()
    
    .checkpoint("solve_puzzle")
        .name("Solve the Puzzle")
        .trigger(CheckpointTrigger.any(
            CheckpointTrigger.http().onEndpoint("/qr/puzzle").build(),
            CheckpointTrigger.redstone().at(50, 65, 50).build()
        ))
        .requires("get_clue")
        .unlocks("find_key")
        .build()
    
    // ...
    .build();
```

---

## Checkpoint Actions

Execute actions when checkpoint completes:

```java
.checkpoint("escape")
    .name("Escape!")
    .triggeredByArea(...)
    .onComplete(
        // Notify team
        CheckpointAction.broadcast("§aYou escaped!"),
        
        // Show title
        CheckpointAction.showTitle("ESCAPED!", "Congratulations!"),
        
        // Trigger physical effects
        CheckpointAction.webhook("http://lights/celebration"),
        
        // Emit redstone (unlock physical door)
        CheckpointAction.emitRedstone(100, 65, 0, 15, 100),
        
        // Make NPC react
        CheckpointAction.npcSpeak("guide", "Well done, adventurers!"),
        
        // Update progress
        CheckpointAction.setProgress("escaped", true)
    )
    .build()
```

---

## JSON Configuration

Adventures can be defined in JSON for easy editing:

```json
{
  "id": "escape_room",
  "name": "Escape Room",
  "checkpoints": [
    {
      "id": "solve_puzzle",
      "name": "Solve the Puzzle",
      "trigger": {
        "type": "ANY_OF",
        "triggers": [
          {
            "type": "HTTP_ENDPOINT",
            "endpoint": "/qr/puzzle"
          },
          {
            "type": "REDSTONE_ON",
            "position": [50, 65, 50]
          }
        ]
      },
      "requires": ["get_clue"],
      "unlocks": ["find_key"],
      "points": 25,
      "onComplete": [
        {
          "action": "WEBHOOK",
          "url": "http://lights/puzzle_solved"
        }
      ]
    }
  ]
}
```

---

## Integration Example: Complete Flow

**Scenario:** Player scans QR code → NPC reveals secret → Checkpoint completes → Door opens

```
1. Physical QR code scanned
   │
   ▼
2. HTTP POST to http://minecraft:8080/qr/clue1
   │
   ▼
3. Eira Relay receives request
   │
   ├──► Emits redstone signal
   │
   └──► Publishes HttpReceivedEvent
        │
        ▼
4. CheckpointTracker checks event
   │
   ├── Matches "find_qr_code" checkpoint? ✓
   │
   └──► Completes checkpoint
        │
        ├──► Unlocks "talk_to_oracle"
        │
        └──► Executes actions:
             • NPC speaks: "Ah, you found the ancient marking!"
             • Team notified: "[New Objective] Talk to the Oracle"
             │
             ▼
5. Player talks to Oracle NPC
   │
   ▼
6. Eira NPC publishes ConversationStartedEvent
   │
   ▼
7. CheckpointTracker completes "talk_to_oracle"
   │
   ├──► Unlocks "get_secret"
   │
   └──► Actions executed
        │
        ▼
8. After 5+ messages, Oracle reveals secret
   │
   ▼
9. Eira NPC publishes SecretRevealedEvent(level=1)
   │
   ▼
10. CheckpointTracker completes "get_secret"
    │
    ├──► Actions:
    │    • emitRedstone(100, 65, 0) ──► Opens door
    │    • webhook("http://lights/dramatic")
    │
    └──► Unlocks next checkpoint
```

---

## Best Practices

### 1. Design for Multiple Paths

```java
// Allow QR code OR manual solution
CheckpointTrigger.any(
    CheckpointTrigger.http().onEndpoint("/puzzle/qr").build(),
    CheckpointTrigger.redstone().at(50, 65, 50).build()
);
```

### 2. Provide Hints

```java
.checkpoint("find_key")
    .hintFrom("guide_npc", "Have you checked behind the bookshelf?")
    .build()
```

### 3. Use Optional Checkpoints for Bonus Content

```java
.checkpoint("secret_room")
    .optional()
    .hidden()  // Don't show until prerequisites met
    .points(100)
    .build()
```

### 4. Chain Physical and Digital Events

```java
// QR scan → NPC speaks → Redstone opens door
.checkpoint("unlock_door")
    .trigger(CheckpointTrigger.sequence(
        CheckpointTrigger.http().onEndpoint("/qr/door_code").build(),
        CheckpointTrigger.npc().onConversationWith("door_guard").build()
    ))
    .onComplete(CheckpointAction.emitRedstone(x, y, z, 15, 100))
    .build()
```

### 5. Use Webhooks for External Feedback

```java
.onComplete(
    CheckpointAction.webhook("http://dashboard/checkpoint_complete"),
    CheckpointAction.webhook("http://lights/success_flash")
)
```

---

## API Reference

### CheckpointTrigger Factory Methods

| Method | Description |
|--------|-------------|
| `redstone()` | Redstone signal triggers |
| `npc()` | NPC interaction triggers |
| `http()` | HTTP/webhook triggers |
| `game()` | Game event triggers |
| `progress()` | Progress value triggers |
| `all(...)` | All conditions required |
| `any(...)` | Any condition sufficient |
| `sequence(...)` | Ordered conditions |
| `manual()` | API-triggered only |

### Checkpoint.Builder Methods

| Method | Description |
|--------|-------------|
| `trigger(...)` | Set the completion trigger |
| `requires(...)` | Set prerequisite checkpoints |
| `unlocks(...)` | Set dependent checkpoints |
| `optional()` | Not required for completion |
| `hidden()` | Hide until available |
| `points(n)` | Score for completion |
| `hintFrom(npc, msg)` | Set hint NPC and message |
| `onComplete(...)` | Actions on completion |

### CheckpointAction Factory Methods

| Method | Description |
|--------|-------------|
| `broadcast(msg)` | Message to team |
| `showTitle(t, s)` | Title screen |
| `webhook(url)` | HTTP POST |
| `emitRedstone(...)` | Emit redstone signal |
| `npcSpeak(id, msg)` | Make NPC speak |
| `setProgress(k, v)` | Update progress |
