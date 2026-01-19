package org.eira.core.impl;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.eira.core.EiraCore;
import org.eira.core.api.event.EiraEvent;
import org.eira.core.api.event.EiraEventBus;

import java.util.UUID;

/**
 * Bridges NeoForge game events to the Eira API server.
 *
 * <p>Forwards relevant Minecraft events to the external API server
 * for checkpoint triggers and game state tracking.
 */
public class NeoForgeEventBridge {

    private final EiraEventBus eventBus;
    private final ApiClient apiClient;
    private final PlayerManagerImpl playerManager;
    private final TeamManagerImpl teamManager;

    private int tickCounter = 0;

    public NeoForgeEventBridge(
            EiraEventBus eventBus,
            ApiClient apiClient,
            PlayerManagerImpl playerManager,
            TeamManagerImpl teamManager
    ) {
        this.eventBus = eventBus;
        this.apiClient = apiClient;
        this.playerManager = playerManager;
        this.teamManager = teamManager;
    }

    // ==================== Player Events ====================

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Initialize player data
            playerManager.onPlayerJoin(player);

            // Forward to API
            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("dimension", player.level().dimension().location().toString());

            apiClient.post("/events/player-join", payload, Void.class);

            // Publish local event
            eventBus.publish(new PlayerJoinedEvent(
                player.getUUID(),
                player.getName().getString(),
                player
            ));

            EiraCore.LOGGER.debug("Player joined: {}", player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Save player data
            playerManager.onPlayerLeave(player);

            // Forward to API
            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());

            apiClient.post("/events/player-leave", payload, Void.class);

            // Publish local event
            eventBus.publish(new PlayerLeftEvent(
                player.getUUID(),
                player.getName().getString()
            ));

            EiraCore.LOGGER.debug("Player left: {}", player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("conqueredEnd", event.isEndConquered());

            apiClient.post("/events/player-respawn", payload, Void.class);

            eventBus.publish(new PlayerRespawnedEvent(
                player.getUUID(),
                player,
                event.isEndConquered()
            ));
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("deathMessage", event.getSource().getLocalizedDeathMessage(player).getString());
            payload.addProperty("dimension", player.level().dimension().location().toString());

            // Add position
            JsonObject position = new JsonObject();
            position.addProperty("x", player.getX());
            position.addProperty("y", player.getY());
            position.addProperty("z", player.getZ());
            payload.add("position", position);

            apiClient.post("/events/player-death", payload, Void.class);

            eventBus.publish(new PlayerDiedEvent(
                player.getUUID(),
                player,
                event.getSource().getLocalizedDeathMessage(player).getString()
            ));
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("fromDimension", event.getFrom().location().toString());
            payload.addProperty("toDimension", event.getTo().location().toString());

            apiClient.post("/events/dimension-change", payload, Void.class);

            eventBus.publish(new DimensionChangedEvent(
                player.getUUID(),
                event.getFrom().location().toString(),
                event.getTo().location().toString()
            ));
        }
    }

    // ==================== Interaction Events ====================

    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockPos pos = event.getPos();
            Block block = event.getLevel().getBlockState(pos).getBlock();

            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("blockId", block.toString());
            payload.addProperty("dimension", player.level().dimension().location().toString());

            JsonObject position = new JsonObject();
            position.addProperty("x", pos.getX());
            position.addProperty("y", pos.getY());
            position.addProperty("z", pos.getZ());
            payload.add("position", position);

            // Get team if player is on one
            teamManager.getTeamOf(player).ifPresent(team -> {
                payload.addProperty("teamId", team.getId().toString());
                payload.addProperty("teamName", team.getName());
            });

            apiClient.post("/events/block-interact", payload, Void.class);

            eventBus.publish(new BlockInteractEvent(
                player,
                pos,
                block.toString()
            ));
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Entity target = event.getTarget();

            JsonObject payload = new JsonObject();
            payload.addProperty("playerId", player.getUUID().toString());
            payload.addProperty("playerName", player.getName().getString());
            payload.addProperty("entityId", target.getUUID().toString());
            payload.addProperty("entityType", target.getType().toString());
            payload.addProperty("dimension", player.level().dimension().location().toString());

            // Check if entity has custom name (often used for NPCs)
            if (target.hasCustomName()) {
                payload.addProperty("entityName", target.getCustomName().getString());
            }

            JsonObject position = new JsonObject();
            position.addProperty("x", target.getX());
            position.addProperty("y", target.getY());
            position.addProperty("z", target.getZ());
            payload.add("position", position);

            // Get team if player is on one
            teamManager.getTeamOf(player).ifPresent(team -> {
                payload.addProperty("teamId", team.getId().toString());
                payload.addProperty("teamName", team.getName());
            });

            apiClient.post("/events/entity-interact", payload, Void.class);

            eventBus.publish(new EntityInteractEvent(
                player,
                target.getUUID(),
                target.getType().toString()
            ));
        }
    }

    // ==================== Server Tick ====================

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        // Publish tick event every 20 ticks (1 second)
        if (tickCounter >= 20) {
            tickCounter = 0;
            eventBus.publish(new SecondTickEvent(
                event.getServer(),
                event.getServer().getTickCount()
            ));
        }
    }

    // ==================== World Events ====================

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.world.level.Level level) {
            String dimension = level.dimension().location().toString();

            JsonObject payload = new JsonObject();
            payload.addProperty("dimension", dimension);

            apiClient.post("/events/world-load", payload, Void.class);

            eventBus.publish(new WorldLoadedEvent(dimension));
        }
    }

    @SubscribeEvent
    public void onWorldSave(LevelEvent.Save event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof net.minecraft.world.level.Level level) {
            String dimension = level.dimension().location().toString();

            eventBus.publish(new WorldSavedEvent(dimension));
        }
    }

    // ==================== Local Event Types ====================

    /**
     * Published when a player joins the server.
     */
    public record PlayerJoinedEvent(
        UUID playerId,
        String playerName,
        ServerPlayer player
    ) implements EiraEvent {}

    /**
     * Published when a player leaves the server.
     */
    public record PlayerLeftEvent(
        UUID playerId,
        String playerName
    ) implements EiraEvent {}

    /**
     * Published when a player respawns.
     */
    public record PlayerRespawnedEvent(
        UUID playerId,
        ServerPlayer player,
        boolean conqueredEnd
    ) implements EiraEvent {}

    /**
     * Published when a player dies.
     */
    public record PlayerDiedEvent(
        UUID playerId,
        ServerPlayer player,
        String deathMessage
    ) implements EiraEvent {}

    /**
     * Published when a player changes dimension.
     */
    public record DimensionChangedEvent(
        UUID playerId,
        String fromDimension,
        String toDimension
    ) implements EiraEvent {}

    /**
     * Published when a player interacts with a block.
     */
    public record BlockInteractEvent(
        ServerPlayer player,
        BlockPos pos,
        String blockId
    ) implements EiraEvent {}

    /**
     * Published when a player interacts with an entity.
     */
    public record EntityInteractEvent(
        ServerPlayer player,
        UUID entityId,
        String entityType
    ) implements EiraEvent {}

    /**
     * Published every second (20 ticks).
     */
    public record SecondTickEvent(
        MinecraftServer server,
        long tickCount
    ) implements EiraEvent {}

    /**
     * Published when a world is loaded.
     */
    public record WorldLoadedEvent(
        String dimension
    ) implements EiraEvent {}

    /**
     * Published when a world is saved.
     */
    public record WorldSavedEvent(
        String dimension
    ) implements EiraEvent {}
}
