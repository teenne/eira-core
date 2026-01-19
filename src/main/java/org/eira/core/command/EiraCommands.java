package org.eira.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.eira.core.EiraCore;
import org.eira.core.api.adventure.Adventure;
import org.eira.core.api.adventure.AdventureInstance;
import org.eira.core.api.team.Team;

import java.util.Collection;

/**
 * Admin and debug commands for Eira Core.
 *
 * <h2>Commands</h2>
 * <ul>
 *   <li>/eira status - Show connection status</li>
 *   <li>/eira team - Team management</li>
 *   <li>/eira adventure - Adventure management</li>
 *   <li>/eira player - Player progress</li>
 *   <li>/eira debug - Debug utilities</li>
 * </ul>
 */
public class EiraCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("eira")
                .requires(source -> source.hasPermission(2)) // Op level 2+

                // /eira status
                .then(Commands.literal("status")
                    .executes(EiraCommands::showStatus))

                // /eira info
                .then(Commands.literal("info")
                    .executes(EiraCommands::showInfo))

                // /eira reconnect
                .then(Commands.literal("reconnect")
                    .executes(EiraCommands::reconnect))

                // /eira team <subcommand>
                .then(Commands.literal("team")
                    .then(Commands.literal("create")
                        .then(Commands.argument("name", StringArgumentType.string())
                            .executes(EiraCommands::createTeam)
                            .then(Commands.argument("players", EntityArgument.players())
                                .executes(EiraCommands::createTeamWithPlayers))))

                    .then(Commands.literal("disband")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .executes(EiraCommands::disbandTeam)))

                    .then(Commands.literal("add")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(EiraCommands::addToTeam))))

                    .then(Commands.literal("remove")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .then(Commands.argument("player", EntityArgument.player())
                                .executes(EiraCommands::removeFromTeam))))

                    .then(Commands.literal("list")
                        .executes(EiraCommands::listTeams))

                    .then(Commands.literal("info")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .executes(EiraCommands::teamInfo))))

                // /eira adventure <subcommand>
                .then(Commands.literal("adventure")
                    .then(Commands.literal("start")
                        .then(Commands.argument("adventureId", StringArgumentType.string())
                            .then(Commands.argument("teamName", StringArgumentType.string())
                                .executes(EiraCommands::startAdventure))))

                    .then(Commands.literal("stop")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .executes(EiraCommands::stopAdventure)))

                    .then(Commands.literal("checkpoint")
                        .then(Commands.argument("teamName", StringArgumentType.string())
                            .then(Commands.argument("checkpointId", StringArgumentType.string())
                                .executes(EiraCommands::completeCheckpoint))))

                    .then(Commands.literal("list")
                        .executes(EiraCommands::listAdventures))

                    .then(Commands.literal("active")
                        .executes(EiraCommands::listActiveAdventures)))

                // /eira player <subcommand>
                .then(Commands.literal("player")
                    .then(Commands.literal("progress")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(EiraCommands::showPlayerProgress)
                            .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.string())
                                    .then(Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(EiraCommands::setPlayerProgress))))))

                    .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(EiraCommands::resetPlayerProgress))))

                // /eira debug <subcommand>
                .then(Commands.literal("debug")
                    .requires(source -> source.hasPermission(4)) // Server admin only

                    .then(Commands.literal("events")
                        .executes(EiraCommands::showEventStats))

                    .then(Commands.literal("api")
                        .executes(EiraCommands::testApi))

                    .then(Commands.literal("ws")
                        .executes(EiraCommands::testWebSocket)))
        );

        EiraCore.LOGGER.info("Eira Core commands registered");
    }

    // ==================== Status Commands ====================

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var core = EiraCore.getInstance();

        source.sendSuccess(() -> Component.literal("=== Eira Core Status ==="), false);

        // API status
        boolean apiConnected = core.getApiClient() != null && core.getApiClient().isConnected();
        String apiStatus = apiConnected ? "\u00A7aConnected" : "\u00A7cDisconnected";
        source.sendSuccess(() -> Component.literal("  API: " + apiStatus), false);

        // WebSocket status
        boolean wsConnected = core.getWebSocket() != null && core.getWebSocket().isConnected();
        String wsStatus = wsConnected ? "\u00A7aConnected" : "\u00A7cDisconnected";
        source.sendSuccess(() -> Component.literal("  WebSocket: " + wsStatus), false);

        // Config
        source.sendSuccess(() -> Component.literal("  API URL: " + core.getConfig().getApiBaseUrl()), false);
        source.sendSuccess(() -> Component.literal("  WS URL: " + core.getConfig().getWebSocketUrl()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int showInfo(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var core = EiraCore.getInstance();

        source.sendSuccess(() -> Component.literal("=== Eira Core ==="), false);
        source.sendSuccess(() -> Component.literal("  Version: 1.0.0"), false);
        source.sendSuccess(() -> Component.literal("  Teams: " + core.teams().getTeamCount()), false);
        source.sendSuccess(() -> Component.literal("  Adventures: " + core.adventures().getRegisteredAdventures().size()), false);
        source.sendSuccess(() -> Component.literal("  Stories: " + core.stories().getRegisteredStories().size()), false);
        source.sendSuccess(() -> Component.literal("  Active Sessions: " + core.adventures().getActiveInstances().size()), false);

        return Command.SINGLE_SUCCESS;
    }

    private static int reconnect(CommandContext<CommandSourceStack> ctx) {
        var source = ctx.getSource();
        var core = EiraCore.getInstance();

        source.sendSuccess(() -> Component.literal("Reconnecting to API server..."), true);

        if (core.getWebSocket() != null) {
            core.getWebSocket().reconnect();
        }

        // Refresh caches
        core.getTeamManager().refreshCache();
        core.getAdventureManager().loadAdventures();
        core.getStoryManager().loadStories();

        source.sendSuccess(() -> Component.literal("Reconnection initiated"), true);
        return Command.SINGLE_SUCCESS;
    }

    // ==================== Team Commands ====================

    private static int createTeam(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        try {
            Team team = EiraCore.getInstance().teams().create(name).build();
            ctx.getSource().sendSuccess(
                () -> Component.literal("Created team: " + team.getName() + " (ID: " + team.getId() + ")"),
                true
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to create team: " + e.getMessage()));
            return 0;
        }
    }

    private static int createTeamWithPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "name");
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");

        try {
            var builder = EiraCore.getInstance().teams().create(name);
            players.forEach(builder::withMembers);
            Team team = builder.build();

            ctx.getSource().sendSuccess(
                () -> Component.literal("Created team: " + team.getName() + " with " + players.size() + " members"),
                true
            );
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to create team: " + e.getMessage()));
            return 0;
        }
    }

    private static int disbandTeam(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "teamName");
        return EiraCore.getInstance().teams().getByName(name)
            .map(team -> {
                team.disband();
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Disbanded team: " + name),
                    true
                );
                return Command.SINGLE_SUCCESS;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Team not found: " + name));
                return 0;
            });
    }

    private static int addToTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String teamName = StringArgumentType.getString(ctx, "teamName");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

        return EiraCore.getInstance().teams().getByName(teamName)
            .map(team -> {
                if (team.addMember(player)) {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("Added " + player.getName().getString() + " to " + teamName),
                        true
                    );
                    return Command.SINGLE_SUCCESS;
                } else {
                    ctx.getSource().sendFailure(Component.literal("Could not add player (team full or already member)"));
                    return 0;
                }
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Team not found: " + teamName));
                return 0;
            });
    }

    private static int removeFromTeam(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String teamName = StringArgumentType.getString(ctx, "teamName");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

        return EiraCore.getInstance().teams().getByName(teamName)
            .map(team -> {
                if (team.removeMember(player)) {
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("Removed " + player.getName().getString() + " from " + teamName),
                        true
                    );
                    return Command.SINGLE_SUCCESS;
                } else {
                    ctx.getSource().sendFailure(Component.literal("Player is not a member of the team"));
                    return 0;
                }
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Team not found: " + teamName));
                return 0;
            });
    }

    private static int listTeams(CommandContext<CommandSourceStack> ctx) {
        var teams = EiraCore.getInstance().teams().getAll();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Teams (" + teams.size() + ") ==="), false);

        for (Team team : teams) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  - " + team.getName() + " [" + team.getSize() + "/" + team.getMaxSize() + "]"),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int teamInfo(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "teamName");
        return EiraCore.getInstance().teams().getByName(name)
            .map(team -> {
                ctx.getSource().sendSuccess(() -> Component.literal("=== Team: " + team.getName() + " ==="), false);
                ctx.getSource().sendSuccess(() -> Component.literal("  ID: " + team.getId()), false);
                ctx.getSource().sendSuccess(() -> Component.literal("  Color: " + team.getColor().getName()), false);
                ctx.getSource().sendSuccess(() -> Component.literal("  Size: " + team.getSize() + "/" + team.getMaxSize()), false);
                ctx.getSource().sendSuccess(() -> Component.literal("  Members: " + team.getMemberIds().size()), false);
                ctx.getSource().sendSuccess(() -> Component.literal("  Online: " + team.getOnlineMembers().size()), false);
                return Command.SINGLE_SUCCESS;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Team not found: " + name));
                return 0;
            });
    }

    // ==================== Adventure Commands ====================

    private static int startAdventure(CommandContext<CommandSourceStack> ctx) {
        String adventureId = StringArgumentType.getString(ctx, "adventureId");
        String teamName = StringArgumentType.getString(ctx, "teamName");

        return EiraCore.getInstance().teams().getByName(teamName)
            .map(team -> {
                try {
                    AdventureInstance instance = EiraCore.getInstance().adventures().start(adventureId, team);
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("Started adventure '" + adventureId + "' for team " + teamName),
                        true
                    );
                    return Command.SINGLE_SUCCESS;
                } catch (Exception e) {
                    ctx.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
                    return 0;
                }
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("Team not found: " + teamName));
                return 0;
            });
    }

    private static int stopAdventure(CommandContext<CommandSourceStack> ctx) {
        String teamName = StringArgumentType.getString(ctx, "teamName");

        return EiraCore.getInstance().teams().getByName(teamName)
            .flatMap(team -> EiraCore.getInstance().adventures().getInstanceForTeam(team))
            .map(instance -> {
                instance.fail("Stopped by admin");
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Stopped adventure for team " + teamName),
                    true
                );
                return Command.SINGLE_SUCCESS;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("No active adventure for team: " + teamName));
                return 0;
            });
    }

    private static int completeCheckpoint(CommandContext<CommandSourceStack> ctx) {
        String teamName = StringArgumentType.getString(ctx, "teamName");
        String checkpointId = StringArgumentType.getString(ctx, "checkpointId");

        return EiraCore.getInstance().teams().getByName(teamName)
            .flatMap(team -> EiraCore.getInstance().adventures().getInstanceForTeam(team))
            .map(instance -> {
                instance.completeCheckpoint(checkpointId);
                ctx.getSource().sendSuccess(
                    () -> Component.literal("Checkpoint '" + checkpointId + "' completed for team " + teamName),
                    true
                );
                return Command.SINGLE_SUCCESS;
            })
            .orElseGet(() -> {
                ctx.getSource().sendFailure(Component.literal("No active adventure for team: " + teamName));
                return 0;
            });
    }

    private static int listAdventures(CommandContext<CommandSourceStack> ctx) {
        var adventures = EiraCore.getInstance().adventures().getRegisteredAdventures();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Adventures (" + adventures.size() + ") ==="), false);

        for (Adventure adventure : adventures) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  - " + adventure.getId() + ": " + adventure.getName()),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int listActiveAdventures(CommandContext<CommandSourceStack> ctx) {
        var instances = EiraCore.getInstance().adventures().getActiveInstances();
        ctx.getSource().sendSuccess(() -> Component.literal("=== Active Adventures (" + instances.size() + ") ==="), false);

        for (AdventureInstance instance : instances) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  - " + instance.getAdventure().getName() +
                    " (Team: " + instance.getTeam().getName() + ", Progress: " +
                    Math.round(instance.getProgress() * 100) + "%)"),
                false
            );
        }
        return Command.SINGLE_SUCCESS;
    }

    // ==================== Player Commands ====================

    private static int showPlayerProgress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        var eiraPlayer = EiraCore.getInstance().players().get(player);

        ctx.getSource().sendSuccess(
            () -> Component.literal("=== Progress for " + player.getName().getString() + " ==="),
            false
        );

        var progress = eiraPlayer.getProgress().getAll();
        if (progress.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("  (no progress data)"), false);
        } else {
            for (var entry : progress.entrySet()) {
                ctx.getSource().sendSuccess(
                    () -> Component.literal("  " + entry.getKey() + ": " + entry.getValue()),
                    false
                );
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int setPlayerProgress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        String key = StringArgumentType.getString(ctx, "key");
        int value = IntegerArgumentType.getInteger(ctx, "value");

        var eiraPlayer = EiraCore.getInstance().players().get(player);
        eiraPlayer.getProgress().set(key, value);

        ctx.getSource().sendSuccess(
            () -> Component.literal("Set " + player.getName().getString() + "'s progress." + key + " = " + value),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int resetPlayerProgress(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

        var eiraPlayer = EiraCore.getInstance().players().get(player);
        eiraPlayer.getProgress().clear();

        ctx.getSource().sendSuccess(
            () -> Component.literal("Reset progress for " + player.getName().getString()),
            true
        );
        return Command.SINGLE_SUCCESS;
    }

    // ==================== Debug Commands ====================

    private static int showEventStats(CommandContext<CommandSourceStack> ctx) {
        var stats = EiraCore.getInstance().getEventBus().getStats();

        ctx.getSource().sendSuccess(() -> Component.literal("=== Eira Event Bus Stats ==="), false);
        for (var entry : stats.entrySet()) {
            ctx.getSource().sendSuccess(
                () -> Component.literal("  " + entry.getKey() + ": " + entry.getValue()),
                false
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int testApi(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Testing API connection..."), true);

        EiraCore.getInstance().getApiClient().healthCheck()
            .thenAccept(healthy -> {
                if (healthy) {
                    ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aAPI health check passed"), true);
                } else {
                    ctx.getSource().sendFailure(Component.literal("\u00A7cAPI health check failed"));
                }
            });

        return Command.SINGLE_SUCCESS;
    }

    private static int testWebSocket(CommandContext<CommandSourceStack> ctx) {
        var ws = EiraCore.getInstance().getWebSocket();

        if (ws.isConnected()) {
            ctx.getSource().sendSuccess(() -> Component.literal("\u00A7aWebSocket connected"), false);
            ws.send("PING", null);
            ctx.getSource().sendSuccess(() -> Component.literal("Ping sent"), false);
        } else {
            ctx.getSource().sendFailure(Component.literal("\u00A7cWebSocket not connected"));
        }

        return Command.SINGLE_SUCCESS;
    }
}
