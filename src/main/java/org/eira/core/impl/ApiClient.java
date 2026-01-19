package org.eira.core.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eira.core.EiraCore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * HTTP client for communicating with the Eira API server.
 *
 * <p>Provides async HTTP methods with automatic JSON serialization,
 * authentication, retry logic, and error handling.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ApiClient api = new ApiClient(config);
 *
 * // GET request
 * api.get("/teams", TeamListResponse.class)
 *    .thenAccept(response -> {...});
 *
 * // POST request
 * api.post("/teams", createTeamRequest, TeamResponse.class)
 *    .thenAccept(response -> {...});
 * }</pre>
 */
public class ApiClient {

    private final EiraConfigImpl config;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executor;

    private volatile boolean connected = false;
    private volatile String lastError = null;

    /**
     * Create a new API client.
     *
     * @param config the configuration provider
     */
    public ApiClient(EiraConfigImpl config) {
        this.config = config;
        this.gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "eira-api-client");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.getApiTimeoutMs()))
            .executor(executor)
            .build();
    }

    // ==================== HTTP Methods ====================

    /**
     * Perform an async GET request.
     *
     * @param path endpoint path (appended to base URL)
     * @param responseClass the response class to deserialize to
     * @return CompletableFuture with the response
     */
    public <T> CompletableFuture<ApiResponse<T>> get(String path, Class<T> responseClass) {
        return executeWithRetry(() -> doGet(path, responseClass));
    }

    /**
     * Perform an async POST request.
     *
     * @param path endpoint path
     * @param body request body (will be serialized to JSON)
     * @param responseClass the response class to deserialize to
     * @return CompletableFuture with the response
     */
    public <T> CompletableFuture<ApiResponse<T>> post(String path, Object body, Class<T> responseClass) {
        return executeWithRetry(() -> doPost(path, body, responseClass));
    }

    /**
     * Perform an async PUT request.
     *
     * @param path endpoint path
     * @param body request body (will be serialized to JSON)
     * @param responseClass the response class to deserialize to
     * @return CompletableFuture with the response
     */
    public <T> CompletableFuture<ApiResponse<T>> put(String path, Object body, Class<T> responseClass) {
        return executeWithRetry(() -> doPut(path, body, responseClass));
    }

    /**
     * Perform an async DELETE request.
     *
     * @param path endpoint path
     * @param responseClass the response class to deserialize to
     * @return CompletableFuture with the response
     */
    public <T> CompletableFuture<ApiResponse<T>> delete(String path, Class<T> responseClass) {
        return executeWithRetry(() -> doDelete(path, responseClass));
    }

    /**
     * Perform an async POST request without expecting a response body.
     *
     * @param path endpoint path
     * @param body request body (will be serialized to JSON)
     * @return CompletableFuture with success/failure status
     */
    public CompletableFuture<ApiResponse<Void>> post(String path, Object body) {
        return executeWithRetry(() -> doPost(path, body, Void.class));
    }

    // ==================== Internal HTTP Execution ====================

    private <T> CompletableFuture<ApiResponse<T>> doGet(String path, Class<T> responseClass) {
        HttpRequest request = buildRequest(path)
            .GET()
            .build();
        return execute(request, responseClass);
    }

    private <T> CompletableFuture<ApiResponse<T>> doPost(String path, Object body, Class<T> responseClass) {
        String json = body != null ? gson.toJson(body) : "{}";
        HttpRequest request = buildRequest(path)
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return execute(request, responseClass);
    }

    private <T> CompletableFuture<ApiResponse<T>> doPut(String path, Object body, Class<T> responseClass) {
        String json = body != null ? gson.toJson(body) : "{}";
        HttpRequest request = buildRequest(path)
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return execute(request, responseClass);
    }

    private <T> CompletableFuture<ApiResponse<T>> doDelete(String path, Class<T> responseClass) {
        HttpRequest request = buildRequest(path)
            .DELETE()
            .build();
        return execute(request, responseClass);
    }

    private HttpRequest.Builder buildRequest(String path) {
        String baseUrl = config.getApiBaseUrl();
        String url = baseUrl.endsWith("/") ? baseUrl + path.substring(1) : baseUrl + path;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(config.getApiTimeoutMs()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");

        // Add API key if configured
        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder;
    }

    private <T> CompletableFuture<ApiResponse<T>> execute(HttpRequest request, Class<T> responseClass) {
        if (config.isVerboseLogging()) {
            EiraCore.LOGGER.debug("API Request: {} {}", request.method(), request.uri());
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                connected = true;
                lastError = null;

                if (config.isVerboseLogging()) {
                    EiraCore.LOGGER.debug("API Response: {} - {}", response.statusCode(), response.body());
                }

                return parseResponse(response, responseClass);
            })
            .exceptionally(ex -> {
                connected = false;
                lastError = ex.getMessage();
                EiraCore.LOGGER.error("API request failed: {}", ex.getMessage());
                return ApiResponse.error(ex.getMessage());
            });
    }

    private <T> ApiResponse<T> parseResponse(HttpResponse<String> response, Class<T> responseClass) {
        int status = response.statusCode();
        String body = response.body();

        if (status >= 200 && status < 300) {
            // Success
            if (responseClass == Void.class || body == null || body.isEmpty()) {
                return ApiResponse.success(null);
            }
            try {
                T data = gson.fromJson(body, responseClass);
                return ApiResponse.success(data);
            } catch (Exception e) {
                EiraCore.LOGGER.error("Failed to parse response: {}", e.getMessage());
                return ApiResponse.error("Failed to parse response: " + e.getMessage());
            }
        } else {
            // Error response
            String errorMessage = extractErrorMessage(body, status);
            return ApiResponse.error(errorMessage, status);
        }
    }

    private String extractErrorMessage(String body, int status) {
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json != null && json.has("message")) {
                return json.get("message").getAsString();
            }
            if (json != null && json.has("error")) {
                return json.get("error").getAsString();
            }
        } catch (Exception ignored) {
            // Not JSON or no message field
        }
        return "HTTP " + status + ": " + body;
    }

    // ==================== Retry Logic ====================

    private <T> CompletableFuture<ApiResponse<T>> executeWithRetry(
            Supplier<CompletableFuture<ApiResponse<T>>> requestSupplier) {
        return executeWithRetry(requestSupplier, config.getApiRetryCount());
    }

    private <T> CompletableFuture<ApiResponse<T>> executeWithRetry(
            Supplier<CompletableFuture<ApiResponse<T>>> requestSupplier, int retriesLeft) {

        return requestSupplier.get().thenCompose(response -> {
            if (response.isSuccess() || retriesLeft <= 0) {
                return CompletableFuture.completedFuture(response);
            }

            // Retry on connection errors (not HTTP errors)
            if (response.getStatusCode() == 0) {
                EiraCore.LOGGER.warn("API request failed, retrying ({} attempts left)...", retriesLeft);
                return executeWithRetry(requestSupplier, retriesLeft - 1);
            }

            return CompletableFuture.completedFuture(response);
        });
    }

    // ==================== Health Check ====================

    /**
     * Check if the API server is reachable.
     *
     * @return CompletableFuture with health status
     */
    public CompletableFuture<Boolean> healthCheck() {
        return get("/health", JsonObject.class)
            .thenApply(ApiResponse::isSuccess)
            .exceptionally(ex -> false);
    }

    /**
     * Check if currently connected to the API.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Get the last error message, if any.
     */
    public String getLastError() {
        return lastError;
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the API client and release resources.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Get the Gson instance for external serialization needs.
     */
    public Gson getGson() {
        return gson;
    }

    // ==================== Response Wrapper ====================

    /**
     * Wrapper for API responses providing success/error status and data.
     *
     * @param <T> the response data type
     */
    public static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String error;
        private final int statusCode;

        private ApiResponse(boolean success, T data, String error, int statusCode) {
            this.success = success;
            this.data = data;
            this.error = error;
            this.statusCode = statusCode;
        }

        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, 200);
        }

        public static <T> ApiResponse<T> error(String error) {
            return new ApiResponse<>(false, null, error, 0);
        }

        public static <T> ApiResponse<T> error(String error, int statusCode) {
            return new ApiResponse<>(false, null, error, statusCode);
        }

        public boolean isSuccess() {
            return success;
        }

        public T getData() {
            return data;
        }

        public String getError() {
            return error;
        }

        public int getStatusCode() {
            return statusCode;
        }

        /**
         * Get data or throw if error.
         */
        public T getOrThrow() {
            if (!success) {
                throw new ApiException(error, statusCode);
            }
            return data;
        }

        /**
         * Get data or return default value.
         */
        public T getOrDefault(T defaultValue) {
            return success ? data : defaultValue;
        }
    }

    /**
     * Exception thrown when API calls fail.
     */
    public static class ApiException extends RuntimeException {
        private final int statusCode;

        public ApiException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
