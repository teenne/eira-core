# Eira Core

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green.svg)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-21.4.x-orange.svg)](https://neoforged.net)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Eira Core** is the foundation mod for the Eira ecosystem - a collection of Minecraft mods designed for immersive educational experiences that bridge physical and digital worlds.

## 🎯 What is Eira Core?

Eira Core provides shared infrastructure that allows multiple Eira mods to work together seamlessly:

- **📡 Event Bus** - Cross-mod communication without direct dependencies
- **👥 Team API** - Create and manage teams of players
- **📊 Player API** - Extended player data and progress tracking
- **📖 Story API** - Framework for narrative experiences
- **🏆 Adventure API** - Timed challenges with checkpoints and leaderboards

## 🧩 The Eira Ecosystem

```
┌─────────────────────────────────────────────┐
│               EIRA CORE (API)                │
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
| **Eira Core** | Shared APIs and services (this mod) |
| **Eira Relay** | HTTP communication for IoT/physical world |
| **Eira NPC** | AI-powered storytelling NPCs |
| **Eira Quest** | Quest/objective system (coming soon) |

## 📦 Installation

### For Players

1. Install NeoForge for Minecraft 1.21.4
2. Download Eira Core from releases
3. Place in your `mods/` folder
4. Install other Eira mods as desired

### For Mod Developers

Add to your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "Eira"
        url = uri("https://maven.eira.org/releases")
    }
}

dependencies {
    implementation("org.eira:eira-core:1.0.0")
}
```

Add to your `neoforge.mods.toml`:

```toml
[[dependencies.your_mod]]
    modId = "eira-core"
    type = "required"  # or "optional" for soft dependency
    versionRange = "[1.0.0,)"
    ordering = "AFTER"
    side = "BOTH"
```

## 🚀 Quick Start

### Access the API

```java
import org.eira.core.api.EiraAPI;

// Get the API
EiraAPI eira = EiraAPI.get();

// Or safely for optional dependencies
EiraAPI.ifPresent(eira -> {
    // Use API
});
```

### Subscribe to Events

```java
eira.events().subscribe(TeamCreatedEvent.class, event -> {
    logger.info("Team created: " + event.team().getName());
});
```

### Create a Team

```java
Team team = eira.teams().create("Red Dragons")
    .withColor(ChatFormatting.RED)
    .withMaxSize(4)
    .build();

team.addMember(player);
team.broadcast("Welcome to the team!");
```

### Track Player Progress

```java
EiraPlayer eiraPlayer = eira.players().get(player);
eiraPlayer.getProgress().set("puzzles_solved", 5);
eiraPlayer.getProgress().increment("total_score", 100);
```

## 📚 Documentation

- [API Specification](docs/API.md) - Detailed API reference
- [Event Reference](docs/EVENTS.md) - All built-in events
- [Ecosystem Overview](docs/ECOSYSTEM.md) - How mods work together
- [Integration Guide](docs/INTEGRATION.md) - Integrating your mod

## 🎓 Educational Use Cases

Eira mods are designed for educational installations:

- **Coding Workshops** - Teams compete to solve programming puzzles
- **Museum Exhibits** - Interactive storytelling with physical triggers
- **Escape Rooms** - Minecraft + physical puzzles combined
- **STEM Challenges** - Science experiments trigger in-game events

## 🤝 About Eira

[Eira](https://eira.org) is a non-profit organization focused on teaching kids and teenagers to code and learn technology. Our Minecraft mods create engaging learning experiences that make coding concepts tangible and fun.

## 📄 License

MIT License - See [LICENSE](LICENSE) for details.

---

**Part of the Eira Ecosystem** | [Website](https://eira.org) | [Discord](https://discord.gg/eira) | [GitHub](https://github.com/eira-org)
