# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Eira Core is a NeoForge mod (Minecraft 1.21.4) that provides shared infrastructure for the Eira ecosystem - a collection of mods for immersive educational experiences. It serves as the foundation API that other Eira mods (Eira Relay, Eira NPC, Eira Quest) depend on.

**Architecture:** Eira Core acts as a **stateless gateway** between Minecraft and an external API server. All game data (teams, players, adventures, stories) is stored on the external server.

## Build Commands

```bash
# Build the mod
./gradlew build

# Run Minecraft client with the mod
./gradlew runClient

# Run Minecraft server with the mod
./gradlew runServer

# Generate sources JAR and Javadoc
./gradlew jar sourcesJar javadocJar

# Publish to Maven repository (requires MAVEN_USERNAME/MAVEN_PASSWORD env vars)
./gradlew publish
```

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     EXTERNAL API SERVER                         │
│  (Recommended: Node.js + TypeScript + Fastify + PostgreSQL)    │
│                                                                 │
│  - Teams, Players, Stories, Adventures, Checkpoints            │
│  - Leaderboards, Progress tracking                             │
│  - WebSocket for real-time events                              │
│  - REST API for CRUD operations                                │
└─────────────────────────────────────────────────────────────────┘
                              ▲
                              │ HTTP/WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   EIRA CORE (Minecraft Mod)                     │
│                                                                 │
│  - Gateway: Forward Minecraft events to API                    │
│  - Executor: Execute server instructions (sounds, titles, etc) │
│  - Local event bus for other Eira mods (Relay, NPC, Quest)     │
│  - Stateless: No persistent data storage                       │
└─────────────────────────────────────────────────────────────────┘
```

### Entry Point
- `EiraCore` (src/main/java/org/eira/core/EiraCore.java) - Main mod class that implements `EiraAPI` and initializes all subsystems

### Core Infrastructure (org.eira.core.impl)

| Class | Purpose |
|-------|---------|
| `ApiClient` | Async HTTP client for REST API calls |
| `WebSocketClient` | Real-time bidirectional communication |
| `EiraEventBusImpl` | Local event bus for Eira mods |
| `EiraConfigImpl` | NeoForge config with API settings |

### Manager Implementations (org.eira.core.impl)

| Class | Purpose |
|-------|---------|
| `TeamManagerImpl` | Team CRUD via API, local caching |
| `PlayerManagerImpl` | Player data via API |
| `AdventureManagerImpl` | Adventure lifecycle via API |
| `StoryManagerImpl` | Story/chapter management via API |

### Event/Instruction Handling (org.eira.core.impl)

| Class | Purpose |
|-------|---------|
| `NeoForgeEventBridge` | Forward Minecraft events to API server |
| `InstructionHandler` | Execute API server commands in Minecraft |

### Public API (org.eira.core.api)
The API is accessed via `EiraAPI.get()` or safely via `EiraAPI.ifPresent(eira -> {...})` for soft dependencies.

Subsystems:
- `events()` - Cross-mod event bus for loose coupling between Eira mods
- `teams()` - Team creation/management (stored on API server)
- `players()` - Extended player data and progress tracking (stored on API server)
- `stories()` - Narrative framework with chapters, secrets, and story state
- `adventures()` - Timed challenges with checkpoints, triggers, and leaderboards
- `config()` - Shared configuration management
- `network()` - Cross-mod packet infrastructure

### Event System
Events use a publish/subscribe pattern. Any mod can publish `EiraEvent` implementations and subscribe to events from other mods. Supports priorities and async handlers via `@Subscribe` annotation.

### Eira Relay Compatibility

Eira Core provides a compatibility layer for Eira Relay (HappyHttpMod) integration. The event system uses two packages:

| Package | Purpose |
|---------|---------|
| `org.eira.core.api.event` | Rich event API with priorities, cancellation, async support |
| `org.eira.core.api.events` | Simplified API matching Eira Relay's expected interface |

**Architecture:**
```
Eira Relay
  │ uses org.eira.core.api.events.EiraEventBus
  ▼
CompatibilityEventBusAdapter
  │ delegates to
  ▼
EiraEventBusImpl (internal rich implementation)
```

**Compatibility Events** (org.eira.core.api.events):

| Event | Description |
|-------|-------------|
| `HttpReceivedEvent` | HTTP request received (QR codes, external APIs) |
| `ExternalTriggerEvent` | External triggers (sensors, IoT devices) |
| `RedstoneChangeEvent` | Redstone signal changes |
| `ServerCommandEvent` | Commands from Eira API Server |
| `CheckpointCompletedEvent` | Checkpoint completion events |

Events in the compatibility package extend the rich `EiraEvent`, so they work with both APIs.

### Checkpoint System
Adventures use an event-driven checkpoint system that can be triggered by:
- Redstone signals (from Eira Relay blocks)
- NPC interactions (from Eira NPC)
- HTTP requests (QR codes, sensors, external APIs)
- Game events (area entry, item collection)

Triggers are processed on the API server.

### Commands
Admin commands available via `/eira`:
- `/eira status` - Show API/WebSocket connection status
- `/eira team <create|list|disband|add|remove|info>` - Team management
- `/eira adventure <start|stop|list|active|checkpoint>` - Adventure management
- `/eira player <progress|reset>` - Player data
- `/eira debug <events|api|ws>` - Debug utilities
- `/eira reconnect` - Force reconnection to API server

## Configuration

Configuration file: `config/eira-core.toml`

```toml
[api]
baseUrl = "http://localhost:3000/api"
apiKey = ""
timeoutMs = 10000
retryCount = 3

[websocket]
url = "ws://localhost:3000/ws"
reconnectDelayMs = 5000

[general]
debugMode = false
verboseLogging = false

[teams]
defaultMaxSize = 8
defaultColor = "WHITE"
```

## Key Patterns

- **Stateless Gateway:** All data stored on external API server
- **Async API Calls:** All manager methods use CompletableFuture internally
- **Local Caching:** Teams/adventures cached locally for performance
- **Event Forwarding:** Minecraft events forwarded to API server
- **Instruction Execution:** API server sends commands executed in Minecraft

## Dependencies

- NeoForge 21.4.x (Minecraft 1.21.4)
- Java 21
- Parchment mappings (2024.12.07)
- Gson for JSON handling

## API Server (eira-api)

The companion API server is available at: **https://github.com/teenne/eira-api**

### Quick Start
```bash
git clone https://github.com/teenne/eira-api.git
cd eira-api
npm install
cp .env.example .env  # Edit with your settings
npm run db:push
npm run dev
```

### REST Endpoints
- `GET/POST /api/teams` - Team management
- `GET/PUT /api/players/:uuid` - Player data
- `GET /api/adventures` - Adventure definitions
- `POST /api/adventures/:id/start` - Start adventure
- `GET /api/stories` - Story definitions
- `POST /api/events/*` - Game event forwarding
- `GET /api/health` - Health check (includes DB latency and WebSocket stats)
- `GET /api/ws/stats` - WebSocket connection monitoring

### WebSocket Messages
- `INSTRUCTION` - Commands to execute in Minecraft
- `BATCH_INSTRUCTION` - Multiple commands
- Supported actions: `SHOW_TITLE`, `PLAY_SOUND`, `SEND_MESSAGE`, `TELEPORT`, `GIVE_ITEM`, `RUN_COMMAND`

### Tech Stack
- Runtime: Node.js 20 LTS
- Framework: Fastify 4.x
- Language: TypeScript 5.x
- Database: PostgreSQL 16
- ORM: Prisma 5.x
- Validation: Zod
- Tests: Vitest (71 tests)

### API Server Status
See the [eira-api README](https://github.com/teenne/eira-api#roadmap) for:
- Current feature status
- Roadmap and planned features
- Tech debt and known issues
- Next development phase PR plan

## Recommended Plugins

Install these Claude Code plugins for optimal development experience:

```bash
# oh-my-claudecode - Multi-agent orchestration (27 agents, 28 skills)
/plugin marketplace add https://github.com/Yeachan-Heo/oh-my-claudecode
/plugin install oh-my-claudecode
/oh-my-claudecode:omc-setup

# frontend-design - Production-grade UI/UX (official Anthropic plugin)
/plugin marketplace add anthropics/claude-code
/plugin install frontend-design@claude-code-plugins
```
