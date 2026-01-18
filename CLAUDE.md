# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Eira Core is a NeoForge mod (Minecraft 1.21.4) that provides shared infrastructure for the Eira ecosystem - a collection of mods for immersive educational experiences. It serves as the foundation API that other Eira mods (Eira Relay, Eira NPC, Eira Quest) depend on.

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

### Entry Point
- `EiraCore` (src/main/java/org/eira/core/EiraCore.java) - Main mod class that implements `EiraAPI` and initializes all subsystems

### Public API (org.eira.core.api)
The API is accessed via `EiraAPI.get()` or safely via `EiraAPI.ifPresent(eira -> {...})` for soft dependencies.

Subsystems:
- `events()` - Cross-mod event bus for loose coupling between Eira mods
- `teams()` - Team creation/management with data storage and communication
- `players()` - Extended player data and progress tracking (persists across sessions)
- `stories()` - Narrative framework with chapters, secrets, and story state
- `adventures()` - Timed challenges with checkpoints, triggers, and leaderboards
- `config()` - Shared configuration management
- `network()` - Cross-mod packet infrastructure

### Event System
Events use a publish/subscribe pattern. Any mod can publish `EiraEvent` implementations and subscribe to events from other mods. Supports priorities and async handlers via `@Subscribe` annotation.

### Checkpoint System
Adventures use an event-driven checkpoint system that can be triggered by:
- Redstone signals (from Eira Relay blocks)
- NPC interactions (from Eira NPC)
- HTTP requests (QR codes, sensors, external APIs)
- Game events (area entry, item collection)

Triggers can be composed with `CheckpointTrigger.all()`, `any()`, or `sequence()`.

### Implementation Classes
Internal implementations are in `org.eira.core.impl` (e.g., `EiraEventBusImpl`, `TeamManagerImpl`). These are not part of the public API.

## Key Patterns

- Builder pattern for creating Teams, Stories, Adventures, and Checkpoints
- Implementations receive the event bus in constructor for publishing events
- Data persistence handled via Minecraft's saved data system (SavedData)
- Config uses NeoForge's config spec system

## Dependencies

- NeoForge 21.4.x (Minecraft 1.21.4)
- Java 21
- Parchment mappings (2024.12.07)
- Gson for JSON handling
