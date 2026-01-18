package org.eira.core;

import org.eira.core.api.EiraAPI;
import org.eira.core.api.event.EiraEventBus;
import org.eira.core.api.team.TeamManager;
import org.eira.core.api.player.PlayerManager;
import org.eira.core.api.story.StoryManager;
import org.eira.core.api.adventure.AdventureManager;
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
 * Provides:
 * - Cross-mod event bus
 * - Team management
 * - Player progress tracking
 * - Story framework
 * - Adventure system
 * 
 * @see EiraAPI for the public API
 */
@Mod(EiraCore.MOD_ID)
public class EiraCore implements EiraAPI {
    
    public static final String MOD_ID = "eira-core";
    public static final Logger LOGGER = LoggerFactory.getLogger("EiraCore");
    
    private static EiraCore instance;
    
    // Subsystem implementations
    private final EiraEventBusImpl eventBus;
    private final TeamManagerImpl teamManager;
    private final PlayerManagerImpl playerManager;
    private final StoryManagerImpl storyManager;
    private final AdventureManagerImpl adventureManager;
    private final EiraConfigImpl config;
    private final EiraNetworkImpl network;
    
    public EiraCore(IEventBus modEventBus, ModContainer modContainer) {
        instance = this;
        
        LOGGER.info("Eira Core initializing...");
        
        // Initialize subsystems
        this.eventBus = new EiraEventBusImpl();
        this.teamManager = new TeamManagerImpl(eventBus);
        this.playerManager = new PlayerManagerImpl(eventBus);
        this.storyManager = new StoryManagerImpl(eventBus);
        this.adventureManager = new AdventureManagerImpl(eventBus, teamManager);
        this.config = new EiraConfigImpl();
        this.network = new EiraNetworkImpl();
        
        // Register config
        modContainer.registerConfig(Type.COMMON, EiraConfigImpl.SPEC, "eira-core.toml");
        
        // Register network
        modEventBus.addListener(network::registerPayloads);
        
        // Register game event listeners
        NeoForge.EVENT_BUS.register(this);
        
        // Register API for other mods to access
        EiraAPIProvider.register(this);
        
        LOGGER.info("Eira Core initialized - API available for other mods");
    }
    
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Eira Core: Server starting...");
        
        teamManager.initialize(event.getServer());
        playerManager.initialize(event.getServer());
        storyManager.loadStories();
        adventureManager.loadAdventures();
        
        LOGGER.info("Eira Core: {} teams, {} stories, {} adventures loaded",
            teamManager.getAll().size(),
            storyManager.getRegisteredStories().size(),
            adventureManager.getRegisteredAdventures().size()
        );
    }
    
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        // Tick subsystems that need it
        adventureManager.tick(event.getServer());
    }
    
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Eira Core: Server stopping, saving data...");
        
        teamManager.saveAll();
        playerManager.saveAll();
        adventureManager.shutdown();
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
}
