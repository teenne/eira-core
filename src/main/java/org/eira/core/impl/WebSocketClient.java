package org.eira.core.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.eira.core.EiraCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * WebSocket client for real-time communication with the Eira API server.
 *
 * <p>Handles:
 * <ul>
 *   <li>Connection management with auto-reconnect</li>
 *   <li>Message sending and receiving</li>
 *   <li>Heartbeat/keepalive</li>
 *   <li>JSON message protocol</li>
 * </ul>
 *
 * <h2>Message Protocol</h2>
 * All messages are JSON objects with at minimum a "type" field:
 * <pre>{@code
 * {
 *   "type": "INSTRUCTION",
 *   "data": { ... }
 * }
 * }</pre>
 */
public class WebSocketClient {

    private final EiraConfigImpl config;
    private final Gson gson;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Consumer<JsonObject>> messageHandlers = new ConcurrentHashMap<>();

    private volatile WebSocket webSocket;
    private volatile boolean connected = false;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;
    private volatile ScheduledFuture<?> heartbeatTask;
    private volatile ScheduledFuture<?> reconnectTask;

    // Message accumulator for fragmented messages
    private final StringBuilder messageBuffer = new StringBuilder();

    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /**
     * Create a new WebSocket client.
     *
     * @param config the configuration provider
     */
    public WebSocketClient(EiraConfigImpl config) {
        this.config = config;
        this.gson = new GsonBuilder().create();
        this.httpClient = HttpClient.newHttpClient();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "eira-websocket");
            t.setDaemon(true);
            return t;
        });
    }

    // ==================== Connection Management ====================

    /**
     * Connect to the WebSocket server.
     *
     * @return CompletableFuture that completes when connected
     */
    public CompletableFuture<Boolean> connect() {
        if (connected) {
            return CompletableFuture.completedFuture(true);
        }

        shouldReconnect = true;
        return doConnect();
    }

    private CompletableFuture<Boolean> doConnect() {
        String wsUrl = config.getWebSocketUrl();
        EiraCore.LOGGER.info("Connecting to WebSocket: {}", wsUrl);

        return httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(wsUrl), new WebSocketListener())
            .thenApply(ws -> {
                this.webSocket = ws;
                this.connected = true;
                this.reconnectAttempts = 0;
                startHeartbeat();
                EiraCore.LOGGER.info("WebSocket connected successfully");
                return true;
            })
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("WebSocket connection failed: {}", ex.getMessage());
                connected = false;
                scheduleReconnect();
                return false;
            });
    }

    /**
     * Disconnect from the WebSocket server.
     */
    public void disconnect() {
        shouldReconnect = false;
        stopHeartbeat();
        cancelReconnect();

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client disconnect")
                    .thenRun(() -> EiraCore.LOGGER.info("WebSocket disconnected"));
            } catch (Exception e) {
                EiraCore.LOGGER.warn("Error during WebSocket disconnect: {}", e.getMessage());
            }
        }
        connected = false;
    }

    /**
     * Check if currently connected.
     */
    public boolean isConnected() {
        return connected;
    }

    // ==================== Reconnection ====================

    private void scheduleReconnect() {
        if (!shouldReconnect) {
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            EiraCore.LOGGER.error("Max reconnect attempts ({}) reached, giving up", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        reconnectAttempts++;
        int delay = config.getWebSocketReconnectDelayMs();
        // Exponential backoff (capped at 60 seconds)
        int backoffDelay = Math.min(delay * reconnectAttempts, 60000);

        EiraCore.LOGGER.info("Scheduling reconnect attempt {} in {}ms", reconnectAttempts, backoffDelay);

        reconnectTask = scheduler.schedule(() -> {
            if (shouldReconnect && !connected) {
                doConnect();
            }
        }, backoffDelay, TimeUnit.MILLISECONDS);
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
            reconnectTask = null;
        }
    }

    /**
     * Force a reconnection attempt.
     */
    public void reconnect() {
        disconnect();
        reconnectAttempts = 0;
        shouldReconnect = true;
        connect();
    }

    // ==================== Heartbeat ====================

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (connected) {
                sendPing();
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
            heartbeatTask = null;
        }
    }

    private void sendPing() {
        if (webSocket != null && connected) {
            try {
                webSocket.sendPing(ByteBuffer.wrap("ping".getBytes()));
            } catch (Exception e) {
                EiraCore.LOGGER.warn("Failed to send ping: {}", e.getMessage());
            }
        }
    }

    // ==================== Message Sending ====================

    /**
     * Send a message to the server.
     *
     * @param type message type
     * @param data message data
     * @return CompletableFuture that completes when sent
     */
    public CompletableFuture<Void> send(String type, Object data) {
        if (!connected || webSocket == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected")
            );
        }

        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        if (data != null) {
            message.add("data", gson.toJsonTree(data));
        }

        String json = gson.toJson(message);

        if (config.isVerboseLogging()) {
            EiraCore.LOGGER.debug("WS Send: {}", json);
        }

        return webSocket.sendText(json, true)
            .thenAccept(ws -> {})
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to send WebSocket message: {}", ex.getMessage());
                return null;
            });
    }

    /**
     * Send a raw JSON message.
     *
     * @param json the JSON message
     * @return CompletableFuture that completes when sent
     */
    public CompletableFuture<Void> sendRaw(String json) {
        if (!connected || webSocket == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected")
            );
        }

        return webSocket.sendText(json, true)
            .thenAccept(ws -> {})
            .exceptionally(ex -> {
                EiraCore.LOGGER.error("Failed to send WebSocket message: {}", ex.getMessage());
                return null;
            });
    }

    // ==================== Message Handling ====================

    /**
     * Register a handler for a specific message type.
     *
     * @param type the message type
     * @param handler the handler to invoke
     */
    public void onMessage(String type, Consumer<JsonObject> handler) {
        messageHandlers.put(type, handler);
    }

    /**
     * Remove a handler for a message type.
     *
     * @param type the message type
     */
    public void removeHandler(String type) {
        messageHandlers.remove(type);
    }

    private void handleMessage(String json) {
        if (config.isVerboseLogging()) {
            EiraCore.LOGGER.debug("WS Recv: {}", json);
        }

        try {
            JsonObject message = gson.fromJson(json, JsonObject.class);

            if (!message.has("type")) {
                EiraCore.LOGGER.warn("Received WebSocket message without type: {}", json);
                return;
            }

            String type = message.get("type").getAsString();
            Consumer<JsonObject> handler = messageHandlers.get(type);

            if (handler != null) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    EiraCore.LOGGER.error("Error in message handler for type {}: {}",
                        type, e.getMessage(), e);
                }
            } else if (config.isDebugMode()) {
                EiraCore.LOGGER.debug("No handler for message type: {}", type);
            }
        } catch (Exception e) {
            EiraCore.LOGGER.error("Failed to parse WebSocket message: {}", e.getMessage());
        }
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the WebSocket client and release resources.
     */
    public void shutdown() {
        disconnect();
        scheduler.shutdown();
    }

    // ==================== WebSocket Listener ====================

    private class WebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            EiraCore.LOGGER.debug("WebSocket onOpen");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if (last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);
                handleMessage(message);
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            EiraCore.LOGGER.debug("WebSocket pong received");
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            EiraCore.LOGGER.info("WebSocket closed: {} - {}", statusCode, reason);
            connected = false;
            stopHeartbeat();

            if (shouldReconnect && statusCode != WebSocket.NORMAL_CLOSURE) {
                scheduleReconnect();
            }

            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            EiraCore.LOGGER.error("WebSocket error: {}", error.getMessage());
            connected = false;
            stopHeartbeat();

            if (shouldReconnect) {
                scheduleReconnect();
            }
        }
    }
}
