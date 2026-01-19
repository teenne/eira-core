package org.eira.core.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.eira.core.EiraCore;
import org.eira.core.api.team.Team;

import java.util.*;

/**
 * Handles instructions received from the API server via WebSocket.
 *
 * <p>Executes various actions in Minecraft such as:
 * <ul>
 *   <li>Showing titles and messages</li>
 *   <li>Playing sounds</li>
 *   <li>Teleporting players</li>
 *   <li>Giving items</li>
 *   <li>Running commands</li>
 * </ul>
 *
 * <h2>Message Format</h2>
 * <pre>{@code
 * {
 *   "type": "INSTRUCTION",
 *   "data": {
 *     "action": "SHOW_TITLE",
 *     "target": "player:uuid" | "team:id" | "all",
 *     "params": { ... }
 *   }
 * }
 * }</pre>
 */
public class InstructionHandler {

    private final TeamManagerImpl teamManager;
    private final WebSocketClient webSocket;

    private MinecraftServer server;

    public InstructionHandler(TeamManagerImpl teamManager, WebSocketClient webSocket) {
        this.teamManager = teamManager;
        this.webSocket = webSocket;

        // Register WebSocket message handlers
        webSocket.onMessage("INSTRUCTION", this::handleInstruction);
        webSocket.onMessage("BATCH_INSTRUCTION", this::handleBatchInstruction);
    }

    /**
     * Initialize with the Minecraft server instance.
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
    }

    // ==================== Message Handlers ====================

    private void handleInstruction(JsonObject message) {
        if (!message.has("data")) {
            EiraCore.LOGGER.warn("Instruction message missing 'data' field");
            return;
        }

        JsonObject data = message.getAsJsonObject("data");
        executeInstruction(data);
    }

    private void handleBatchInstruction(JsonObject message) {
        if (!message.has("data") || !message.get("data").isJsonArray()) {
            EiraCore.LOGGER.warn("Batch instruction message missing 'data' array");
            return;
        }

        JsonArray instructions = message.getAsJsonArray("data");
        for (var elem : instructions) {
            if (elem.isJsonObject()) {
                executeInstruction(elem.getAsJsonObject());
            }
        }
    }

    private void executeInstruction(JsonObject instruction) {
        if (!instruction.has("action")) {
            EiraCore.LOGGER.warn("Instruction missing 'action' field");
            return;
        }

        String action = instruction.get("action").getAsString();
        String target = instruction.has("target") ? instruction.get("target").getAsString() : "all";
        JsonObject params = instruction.has("params") ? instruction.getAsJsonObject("params") : new JsonObject();

        List<ServerPlayer> players = resolveTarget(target);

        if (players.isEmpty() && !target.equals("all")) {
            EiraCore.LOGGER.debug("No players found for target: {}", target);
            return;
        }

        // Execute on main thread
        if (server != null) {
            server.execute(() -> executeAction(action, players, params));
        }
    }

    // ==================== Target Resolution ====================

    private List<ServerPlayer> resolveTarget(String target) {
        if (server == null) return Collections.emptyList();

        if (target.equals("all")) {
            return new ArrayList<>(server.getPlayerList().getPlayers());
        }

        if (target.startsWith("player:")) {
            String uuidStr = target.substring(7);
            try {
                UUID playerId = UUID.fromString(uuidStr);
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                return player != null ? List.of(player) : Collections.emptyList();
            } catch (IllegalArgumentException e) {
                EiraCore.LOGGER.warn("Invalid player UUID: {}", uuidStr);
                return Collections.emptyList();
            }
        }

        if (target.startsWith("team:")) {
            String teamIdStr = target.substring(5);
            try {
                UUID teamId = UUID.fromString(teamIdStr);
                Optional<Team> team = teamManager.getById(teamId);
                if (team.isPresent()) {
                    return team.get().getOnlineMembers().stream()
                        .filter(p -> p instanceof ServerPlayer)
                        .map(p -> (ServerPlayer) p)
                        .toList();
                }
            } catch (IllegalArgumentException e) {
                // Try by name
                Optional<Team> team = teamManager.getByName(teamIdStr);
                if (team.isPresent()) {
                    return team.get().getOnlineMembers().stream()
                        .filter(p -> p instanceof ServerPlayer)
                        .map(p -> (ServerPlayer) p)
                        .toList();
                }
            }
            return Collections.emptyList();
        }

        EiraCore.LOGGER.warn("Unknown target format: {}", target);
        return Collections.emptyList();
    }

    // ==================== Action Execution ====================

    private void executeAction(String action, List<ServerPlayer> players, JsonObject params) {
        try {
            switch (action.toUpperCase()) {
                case "SHOW_TITLE" -> executeShowTitle(players, params);
                case "SHOW_SUBTITLE" -> executeShowSubtitle(players, params);
                case "SHOW_ACTIONBAR" -> executeShowActionBar(players, params);
                case "SEND_MESSAGE" -> executeSendMessage(players, params);
                case "PLAY_SOUND" -> executePlaySound(players, params);
                case "TELEPORT" -> executeTeleport(players, params);
                case "GIVE_ITEM" -> executeGiveItem(players, params);
                case "RUN_COMMAND" -> executeRunCommand(params);
                case "CLEAR_TITLE" -> executeClearTitle(players);
                default -> EiraCore.LOGGER.warn("Unknown instruction action: {}", action);
            }
        } catch (Exception e) {
            EiraCore.LOGGER.error("Error executing instruction {}: {}", action, e.getMessage(), e);
        }
    }

    private void executeShowTitle(List<ServerPlayer> players, JsonObject params) {
        String title = params.has("title") ? params.get("title").getAsString() : "";
        String subtitle = params.has("subtitle") ? params.get("subtitle").getAsString() : "";
        int fadeIn = params.has("fadeIn") ? params.get("fadeIn").getAsInt() : 10;
        int stay = params.has("stay") ? params.get("stay").getAsInt() : 70;
        int fadeOut = params.has("fadeOut") ? params.get("fadeOut").getAsInt() : 20;

        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            if (!subtitle.isEmpty()) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
            }
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(title)));
        }
    }

    private void executeShowSubtitle(List<ServerPlayer> players, JsonObject params) {
        String subtitle = params.has("subtitle") ? params.get("subtitle").getAsString() : "";

        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)));
        }
    }

    private void executeShowActionBar(List<ServerPlayer> players, JsonObject params) {
        String message = params.has("message") ? params.get("message").getAsString() : "";

        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(message)));
        }
    }

    private void executeSendMessage(List<ServerPlayer> players, JsonObject params) {
        String message = params.has("message") ? params.get("message").getAsString() : "";

        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    private void executePlaySound(List<ServerPlayer> players, JsonObject params) {
        String soundId = params.has("sound") ? params.get("sound").getAsString() : "minecraft:entity.experience_orb.pickup";
        float volume = params.has("volume") ? params.get("volume").getAsFloat() : 1.0f;
        float pitch = params.has("pitch") ? params.get("pitch").getAsFloat() : 1.0f;

        ResourceLocation soundLoc = ResourceLocation.tryParse(soundId);
        if (soundLoc == null) {
            EiraCore.LOGGER.warn("Invalid sound ID: {}", soundId);
            return;
        }

        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
        if (sound == null) {
            // Create a simple sound event for custom sounds
            sound = SoundEvent.createVariableRangeEvent(soundLoc);
        }

        for (ServerPlayer player : players) {
            player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
        }
    }

    private void executeTeleport(List<ServerPlayer> players, JsonObject params) {
        if (!params.has("x") || !params.has("y") || !params.has("z")) {
            EiraCore.LOGGER.warn("Teleport instruction missing coordinates");
            return;
        }

        double x = params.get("x").getAsDouble();
        double y = params.get("y").getAsDouble();
        double z = params.get("z").getAsDouble();
        float yaw = params.has("yaw") ? params.get("yaw").getAsFloat() : 0;
        float pitch = params.has("pitch") ? params.get("pitch").getAsFloat() : 0;

        for (ServerPlayer player : players) {
            player.teleportTo(x, y, z);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private void executeGiveItem(List<ServerPlayer> players, JsonObject params) {
        String itemId = params.has("item") ? params.get("item").getAsString() : "minecraft:diamond";
        int count = params.has("count") ? params.get("count").getAsInt() : 1;

        ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
        if (itemLoc == null) {
            EiraCore.LOGGER.warn("Invalid item ID: {}", itemId);
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(itemLoc);
        if (item == null) {
            EiraCore.LOGGER.warn("Item not found: {}", itemId);
            return;
        }

        ItemStack stack = new ItemStack(item, count);

        // Set custom name if provided
        if (params.has("name")) {
            stack.setHoverName(Component.literal(params.get("name").getAsString()));
        }

        for (ServerPlayer player : players) {
            if (!player.getInventory().add(stack.copy())) {
                // Drop item if inventory is full
                player.drop(stack.copy(), false);
            }
        }
    }

    private void executeRunCommand(JsonObject params) {
        if (!params.has("command")) {
            EiraCore.LOGGER.warn("Run command instruction missing 'command' field");
            return;
        }

        String command = params.get("command").getAsString();

        // Security: Only allow whitelisted commands
        if (!isCommandAllowed(command)) {
            EiraCore.LOGGER.warn("Command not allowed: {}", command);
            return;
        }

        if (server != null) {
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                command
            );
        }
    }

    private void executeClearTitle(List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        }
    }

    // ==================== Security ====================

    private static final Set<String> ALLOWED_COMMAND_PREFIXES = Set.of(
        "say",
        "tell",
        "title",
        "playsound",
        "particle",
        "effect",
        "clear",
        "give",
        "tp",
        "teleport",
        "time",
        "weather",
        "gamemode",
        "difficulty",
        "scoreboard",
        "team",
        "bossbar"
    );

    private boolean isCommandAllowed(String command) {
        String firstWord = command.split(" ")[0].toLowerCase();

        // Remove leading slash if present
        if (firstWord.startsWith("/")) {
            firstWord = firstWord.substring(1);
        }

        return ALLOWED_COMMAND_PREFIXES.contains(firstWord);
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown and cleanup.
     */
    public void shutdown() {
        webSocket.removeHandler("INSTRUCTION");
        webSocket.removeHandler("BATCH_INSTRUCTION");
    }
}
