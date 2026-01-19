package org.eira.core.impl;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.eira.core.config.EiraConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuration implementation using NeoForge's config spec system.
 *
 * <p>Provides API connection settings and general mod configuration.
 */
public class EiraConfigImpl implements EiraConfig {

    // Static spec built during class load
    public static final ModConfigSpec SPEC;

    // API Connection settings
    private static final ModConfigSpec.ConfigValue<String> API_BASE_URL;
    private static final ModConfigSpec.ConfigValue<String> API_KEY;
    private static final ModConfigSpec.ConfigValue<String> WEBSOCKET_URL;
    private static final ModConfigSpec.IntValue API_TIMEOUT_MS;
    private static final ModConfigSpec.IntValue API_RETRY_COUNT;
    private static final ModConfigSpec.IntValue WEBSOCKET_RECONNECT_DELAY_MS;

    // General settings
    private static final ModConfigSpec.BooleanValue DEBUG_MODE;
    private static final ModConfigSpec.BooleanValue VERBOSE_LOGGING;

    // Team settings
    private static final ModConfigSpec.IntValue DEFAULT_MAX_TEAM_SIZE;
    private static final ModConfigSpec.ConfigValue<String> DEFAULT_TEAM_COLOR;

    // Dynamic sections for other mods
    private final Map<String, ConfigSectionImpl> sections = new HashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // API Connection settings
        builder.comment("API Server Connection Settings").push("api");

        API_BASE_URL = builder
            .comment("Base URL of the Eira API server (e.g., http://localhost:3000/api)")
            .define("baseUrl", "http://localhost:3000/api");

        API_KEY = builder
            .comment("API key for authentication (leave empty if not required)")
            .define("apiKey", "");

        API_TIMEOUT_MS = builder
            .comment("Request timeout in milliseconds")
            .defineInRange("timeoutMs", 10000, 1000, 60000);

        API_RETRY_COUNT = builder
            .comment("Number of retry attempts for failed requests")
            .defineInRange("retryCount", 3, 0, 10);

        builder.pop();

        // WebSocket settings
        builder.comment("WebSocket Connection Settings").push("websocket");

        WEBSOCKET_URL = builder
            .comment("WebSocket URL for real-time communication (e.g., ws://localhost:3000/ws)")
            .define("url", "ws://localhost:3000/ws");

        WEBSOCKET_RECONNECT_DELAY_MS = builder
            .comment("Delay before reconnection attempts in milliseconds")
            .defineInRange("reconnectDelayMs", 5000, 1000, 60000);

        builder.pop();

        // General settings
        builder.comment("General Settings").push("general");

        DEBUG_MODE = builder
            .comment("Enable debug mode with additional logging and diagnostics")
            .define("debugMode", false);

        VERBOSE_LOGGING = builder
            .comment("Enable verbose logging of API calls and events")
            .define("verboseLogging", false);

        builder.pop();

        // Team settings
        builder.comment("Team Defaults").push("teams");

        DEFAULT_MAX_TEAM_SIZE = builder
            .comment("Default maximum team size")
            .defineInRange("defaultMaxSize", 8, 1, 64);

        DEFAULT_TEAM_COLOR = builder
            .comment("Default team color (Minecraft color name: WHITE, RED, BLUE, etc.)")
            .define("defaultColor", "WHITE");

        builder.pop();

        SPEC = builder.build();
    }

    // ==================== API Connection Accessors ====================

    /**
     * Get the API server base URL.
     */
    public String getApiBaseUrl() {
        return API_BASE_URL.get();
    }

    /**
     * Get the API key for authentication.
     */
    public String getApiKey() {
        return API_KEY.get();
    }

    /**
     * Get the request timeout in milliseconds.
     */
    public int getApiTimeoutMs() {
        return API_TIMEOUT_MS.get();
    }

    /**
     * Get the number of retry attempts.
     */
    public int getApiRetryCount() {
        return API_RETRY_COUNT.get();
    }

    /**
     * Get the WebSocket URL.
     */
    public String getWebSocketUrl() {
        return WEBSOCKET_URL.get();
    }

    /**
     * Get the WebSocket reconnect delay in milliseconds.
     */
    public int getWebSocketReconnectDelayMs() {
        return WEBSOCKET_RECONNECT_DELAY_MS.get();
    }

    /**
     * Check if debug mode is enabled.
     */
    public boolean isDebugMode() {
        return DEBUG_MODE.get();
    }

    /**
     * Check if verbose logging is enabled.
     */
    public boolean isVerboseLogging() {
        return VERBOSE_LOGGING.get();
    }

    /**
     * Get the default max team size.
     */
    public int getDefaultMaxTeamSize() {
        return DEFAULT_MAX_TEAM_SIZE.get();
    }

    /**
     * Get the default team color name.
     */
    public String getDefaultTeamColor() {
        return DEFAULT_TEAM_COLOR.get();
    }

    // ==================== EiraConfig Interface Implementation ====================

    @Override
    public String getString(String key, String defaultValue) {
        return switch (key) {
            case "api.baseUrl" -> API_BASE_URL.get();
            case "api.apiKey" -> API_KEY.get();
            case "websocket.url" -> WEBSOCKET_URL.get();
            case "teams.defaultColor" -> DEFAULT_TEAM_COLOR.get();
            default -> defaultValue;
        };
    }

    @Override
    public int getInt(String key, int defaultValue) {
        return switch (key) {
            case "api.timeoutMs" -> API_TIMEOUT_MS.get();
            case "api.retryCount" -> API_RETRY_COUNT.get();
            case "websocket.reconnectDelayMs" -> WEBSOCKET_RECONNECT_DELAY_MS.get();
            case "teams.defaultMaxSize" -> DEFAULT_MAX_TEAM_SIZE.get();
            default -> defaultValue;
        };
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return switch (key) {
            case "general.debugMode" -> DEBUG_MODE.get();
            case "general.verboseLogging" -> VERBOSE_LOGGING.get();
            default -> defaultValue;
        };
    }

    @Override
    public double getDouble(String key, double defaultValue) {
        // No double values currently
        return defaultValue;
    }

    @Override
    public ConfigSection getSection(String path) {
        return sections.computeIfAbsent(path, ConfigSectionImpl::new);
    }

    @Override
    public void registerSection(String modId, Consumer<ConfigSection> defaults) {
        ConfigSectionImpl section = new ConfigSectionImpl(modId);
        defaults.accept(section);
        sections.put(modId, section);
    }

    // ==================== Inner Classes ====================

    /**
     * Runtime config section for dynamic mod configuration.
     */
    private static class ConfigSectionImpl implements ConfigSection {
        private final String path;
        private final Map<String, Object> values = new HashMap<>();

        ConfigSectionImpl(String path) {
            this.path = path;
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String s ? s : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Number n ? n.intValue() : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean b ? b : defaultValue;
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            Object value = values.get(key);
            return value instanceof Number n ? n.doubleValue() : defaultValue;
        }

        @Override
        public void set(String key, Object value) {
            values.put(key, value);
        }
    }
}
