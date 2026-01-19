# Eira Core

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.4.x-orange.svg)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Eira Core** is the foundation mod for the Eira ecosystem - a collection of Minecraft mods designed for immersive educational experiences that bridge physical and digital worlds.

## Overview

Eira Core acts as a **gateway between Minecraft and an external API server**. It forwards game events to the server and executes instructions received back, enabling:

- Real-time synchronization across multiple Minecraft servers
- Persistent data storage outside of Minecraft
- Integration with web dashboards and external systems
- Scalable multi-server educational experiences

```
┌─────────────────────────────────────────────────────────────────┐
│                     EXTERNAL API SERVER                         │
│        (REST API + WebSocket + PostgreSQL)                      │
│                                                                 │
│  Teams, Players, Stories, Adventures, Leaderboards              │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ HTTP / WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   EIRA CORE (Minecraft Mod)                     │
│                                                                 │
│  - Forwards Minecraft events to API                             │
│  - Executes server instructions (titles, sounds, teleports)     │
│  - Provides local event bus for other Eira mods                 │
└─────────────────────────────────────────────────────────────────┘
```

## Features

| Feature | Description |
|---------|-------------|
| **Event Bus** | Cross-mod communication without direct dependencies |
| **Team API** | Create and manage teams of players |
| **Player API** | Extended player data and progress tracking |
| **Story API** | Framework for narrative experiences with chapters and secrets |
| **Adventure API** | Timed challenges with checkpoints and leaderboards |
| **Instruction Handler** | Execute commands from the API server (titles, sounds, teleports, items) |

## The Eira Ecosystem

```
┌─────────────────────────────────────────────┐
│               EIRA CORE (API)               │
│  Events • Teams • Stories • Adventures      │
└─────────────────────────────────────────────┘
        ▲              ▲              ▲
        │              │              │
   ┌────┴────┐    ┌────┴────┐    ┌────┴────┐
   │  Eira   │    │  Eira   │    │  Eira   │
   │  Relay  │    │   NPC   │    │  Quest  │
   └─────────┘    └─────────┘    └─────────┘
```

| Mod | Purpose |
|-----|---------|
| **Eira Core** | Shared APIs and gateway to external server (this mod) |
| **Eira Relay** | HTTP communication for IoT/physical world integration |
| **Eira NPC** | AI-powered storytelling NPCs |
| **Eira Quest** | Quest/objective system |

## Requirements

- Minecraft 1.21.4
- NeoForge 21.4.x
- Java 21
- [Eira API Server](https://github.com/teenne/eira-api) (for full functionality)

## Installation

### For Server Operators

1. Install NeoForge for Minecraft 1.21.4
2. Download Eira Core from [releases](https://github.com/eira-org/eira-core/releases)
3. Place the JAR in your `mods/` folder
4. Configure the API server connection (see Configuration below)
5. Start the Minecraft server

### For Developers

Clone and build from source:

```bash
git clone https://github.com/eira-org/eira-core.git
cd eira-core
./gradlew build
```

The built JAR will be in `build/libs/`.

## Configuration

Create or edit `config/eiracore.toml`:

```toml
[api]
    # Base URL of the Eira API server
    baseUrl = "http://localhost:3000/api"
    # API key for authentication (optional)
    apiKey = ""
    # Request timeout in milliseconds
    timeoutMs = 10000
    # Number of retry attempts for failed requests
    retryCount = 3

[websocket]
    # WebSocket URL for real-time communication
    url = "ws://localhost:3000/ws"
    # Delay before reconnection attempts in milliseconds
    reconnectDelayMs = 5000

[general]
    # Enable debug mode with additional logging
    debugMode = false
    # Enable verbose logging of API calls
    verboseLogging = false

[teams]
    # Default maximum team size
    defaultMaxSize = 8
    # Default team color
    defaultColor = "WHITE"
```

## In-Game Commands

All commands require operator permissions.

### Team Commands
```
/eira team create <name>              - Create a new team
/eira team disband <name>             - Disband a team
/eira team add <team> <player>        - Add player to team
/eira team remove <team> <player>     - Remove player from team
/eira team list                       - List all teams
/eira team info <name>                - Show team details
```

### Player Commands
```
/eira player progress <player>        - Show player progress
/eira player set <player> <key> <val> - Set progress value
/eira player reset <player>           - Reset player progress
```

### Adventure Commands
```
/eira adventure list                  - List available adventures
/eira adventure start <id> <team>     - Start adventure for team
/eira adventure stop <team>           - Stop team's adventure
```

### Debug Commands
```
/eira status                          - Show API connection status
/eira debug                           - Toggle debug mode
```

## API Usage (For Mod Developers)

### Add Dependency

In your `build.gradle`:

```groovy
repositories {
    maven { url "https://maven.eira.org/releases" }
}

dependencies {
    implementation "org.eira:eira-core:1.0.0"
}
```

In your `neoforge.mods.toml`:

```toml
[[dependencies.yourmod]]
    modId = "eiracore"
    type = "required"  # or "optional" for soft dependency
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

### Access the API

```java
import org.eira.core.api.EiraAPI;

// Get the API (throws if not available)
EiraAPI eira = EiraAPI.get();

// Or safely for optional dependencies
EiraAPI.ifPresent(eira -> {
    // Use API here
});
```

### Subscribe to Events

```java
eira.events().subscribe(EiraEvents.TeamCreatedEvent.class, event -> {
    logger.info("Team created: " + event.team().getName());
});
```

### Work with Teams

```java
// Create a team
Team team = eira.teams().create("Red Dragons")
    .withColor(ChatFormatting.RED)
    .withMaxSize(4)
    .build();

// Add members
team.addMember(player);

// Broadcast to team
team.broadcast(Component.literal("Welcome to the team!"));

// Get player's team
Optional<Team> playerTeam = eira.teams().getTeamOf(player);
```

### Track Player Progress

```java
EiraPlayer eiraPlayer = eira.players().get(player);
eiraPlayer.getProgress().set("puzzles_solved", 5);
eiraPlayer.getProgress().increment("total_score", 100);
```

## Development

### Building

```bash
./gradlew build
```

### Running in Development

```bash
# Run Minecraft client with the mod
./gradlew runClient

# Run Minecraft server with the mod
./gradlew runServer
```

### Project Structure

```
src/main/java/org/eira/core/
├── EiraCore.java           # Main mod class
├── api/                    # Public API interfaces
│   ├── EiraAPI.java
│   ├── adventure/          # Adventure system
│   ├── event/              # Event bus
│   ├── player/             # Player management
│   ├── story/              # Story framework
│   └── team/               # Team management
├── command/                # In-game commands
├── config/                 # Configuration
├── impl/                   # Internal implementations
│   ├── ApiClient.java      # HTTP client for API server
│   ├── WebSocketClient.java # WebSocket for real-time updates
│   └── ...
└── network/                # Packet infrastructure
```

## Educational Use Cases

Eira mods are designed for educational installations:

- **Coding Workshops** - Teams compete to solve programming puzzles
- **Museum Exhibits** - Interactive storytelling with physical triggers
- **Escape Rooms** - Minecraft + physical puzzles combined
- **STEM Challenges** - Science experiments trigger in-game events

## Related Projects

- [Eira API](https://github.com/teenne/eira-api) - The backend server for Eira Core
- [Eira Relay](https://github.com/eira-org/eira-relay) - IoT/HTTP integration
- [Eira NPC](https://github.com/eira-org/eira-npc) - AI-powered NPCs

## License

MIT License - See [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

---

**Part of the Eira Ecosystem** | [GitHub](https://github.com/eira-org)
