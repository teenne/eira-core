package org.eira.core;

import org.eira.core.api.EiraAPI;
import org.eira.core.api.EiraAPIProvider;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.team.TeamManager;
import org.eira.core.api.player.PlayerManager;
import org.eira.core.api.story.StoryManager;
import org.eira.core.api.adventure.AdventureManager;
import org.eira.core.command.EiraCommands;
import org.eira.core.config.EiraConfig;
import org.eira.core.impl.*;
import org.eira.core.network.EiraNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Eira Core - Foundation mod for the Eira ecosystem.
 *
 * <p>Provides:
 * <ul>
 *   <li>Cross-mod event bus</li>
 *   <li>Team management</li>
 *   <li>Player progress tracking</li>
 *   <li>Story framework</li>
 *   <li>Adventure system</li>
 * </ul>
 *
 * <p>All data is stored on an external API server. This mod acts as a gateway
 * between Minecraft and the Eira API.
 *
 * @see EiraAPI for the public API
 */
@Mod(EiraCore.MOD_ID)
public class EiraCore implements EiraAPI {

    public static final String MOD_ID = "eiracore";
    public static final Logger LOGGER = LoggerFactory.getLogger("EiraCore");

    private static EiraCore instance;

    // Core infrastructure
    private final EiraConfigImpl config;
    private final ApiClient apiClient;
    private final WebSocketClient webSocket;
    private final EiraEventBusImpl eventBus;

    // Subsystem implementations
    private final TeamManagerImpl teamManager;
    private final PlayerManagerImpl playerManager;
    private final StoryManagerImpl storyManager;
    private final AdventureManagerImpl adventureManager;

    // Event bridge and instruction handler
    private final NeoForgeEventBridge eventBridge;
    private final InstructionHandler instructionHandler;

    // Network (local packets between Eira mods)
    private final EiraNetworkImpl network;

    public EiraCore(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;

        LOGGER.info("Eira Core initializing...");

        // Initialize configuration
        this.config = new EiraConfigImpl();

        // Initialize core infrastructure
        this.apiClient = new ApiClient(config);
        this.webSocket = new WebSocketClient(config);
        this.eventBus = new EiraEventBusImpl();

        // Initialize subsystems with API client
        this.teamManager = new TeamManagerImpl(eventBus, apiClient);
        this.playerManager = new PlayerManagerImpl(eventBus, apiClient);
        this.storyManager = new StoryManagerImpl(eventBus, apiClient);
        this.adventureManager = new AdventureManagerImpl(eventBus, teamManager, apiClient);

        // Initialize event bridge (forwards Minecraft events to API)
        this.eventBridge = new NeoForgeEventBridge(eventBus, apiClient, playerManager, teamManager);

        // Initialize instruction handler (executes API commands in Minecraft)
        this.instructionHandler = new InstructionHandler(teamManager, webSocket);

        // Initialize local network for inter-mod communication
        this.network = new EiraNetworkImpl();

        // Register config
        modContainer.registerConfig(Type.COMMON, EiraConfigImpl.SPEC, "eiracore.toml");

        // Register network payloads
        modEventBus.addListener(network::registerPayloads);

        // Register game event listeners
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(eventBridge);
        NeoForge.EVENT_BUS.register(EiraCommands.class);

        // Register API for other mods to access
        EiraAPIProvider.register(this);

        LOGGER.info("Eira Core initialized - API available for other mods");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Eira Core: Server starting...");

        // Initialize subsystems with server
        teamManager.initialize(event.getServer());
        playerManager.initialize(event.getServer());
        adventureManager.initialize(event.getServer());
        instructionHandler.initialize(event.getServer());

        // Connect to API server
        apiClient.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                LOGGER.info("Eira Core: API server connection established");

                // Load data from server
                teamManager.refreshCache();
                storyManager.loadStories();
                adventureManager.loadAdventures();

                // Connect WebSocket for real-time updates
                webSocket.connect().thenAccept(connected -> {
                    if (connected) {
                        LOGGER.info("Eira Core: WebSocket connected");
                    } else {
                        LOGGER.warn("Eira Core: WebSocket connection failed (will retry)");
                    }
                });
            } else {
                LOGGER.warn("Eira Core: API server not available - running in offline mode");
            }
        });

        LOGGER.info("Eira Core: Server initialization complete");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Tick adventure manager for time tracking
        adventureManager.tick(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Eira Core: Server stopping...");

        // Shutdown subsystems
        adventureManager.shutdown();
        instructionHandler.shutdown();

        // Disconnect from API
        webSocket.shutdown();
        apiClient.shutdown();
        eventBus.shutdown();

        // Unregister API
        EiraAPIProvider.unregister();

        LOGGER.info("Eira Core: Shutdown complete");
    }

    // ==================== EiraAPI Implementation ====================

    @Override
    public EiraEventBus events() {
        return eventBus;
    }

    @Override
    public TeamManager teams() {
        return teamManager;
    }

    @Override
    public PlayerManager players() {
        return playerManager;
    }

    @Override
    public StoryManager stories() {
        return storyManager;
    }

    @Override
    public AdventureManager adventures() {
        return adventureManager;
    }

    @Override
    public EiraConfig config() {
        return config;
    }

    @Override
    public EiraNetwork network() {
        return network;
    }

    // ==================== Static Access ====================

    /**
     * Get the Eira Core instance.
     * @throws IllegalStateException if called before mod initialization
     */
    public static EiraCore getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EiraCore not yet initialized");
        }
        return instance;
    }

    /**
     * Check if Eira Core is initialized.
     */
    public static boolean isInitialized() {
        return instance != null;
    }

    // ==================== Internal Access (for commands) ====================

    /**
     * Get the configuration implementation.
     */
    public EiraConfigImpl getConfig() {
        return config;
    }

    /**
     * Get the API client.
     */
    public ApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Get the WebSocket client.
     */
    public WebSocketClient getWebSocket() {
        return webSocket;
    }

    /**
     * Get the event bus implementation.
     */
    public EiraEventBusImpl getEventBus() {
        return eventBus;
    }

    /**
     * Get the team manager implementation.
     */
    public TeamManagerImpl getTeamManager() {
        return teamManager;
    }

    /**
     * Get the adventure manager implementation.
     */
    public AdventureManagerImpl getAdventureManager() {
        return adventureManager;
    }

    /**
     * Get the story manager implementation.
     */
    public StoryManagerImpl getStoryManager() {
        return storyManager;
    }
}
