package mu.nada.lifecycle.client;

import mu.nada.lifecycle.config.LifecycleConfig;
import org.slf4j.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class SystemdBridgeClient {

    private final Logger logger;
    private volatile LifecycleConfig.BridgeSettings settings;
    private volatile HttpClient httpClient;

    public SystemdBridgeClient(LifecycleConfig.BridgeSettings settings, Logger logger) {
        this.logger = logger;
        updateSettings(settings);
    }

    public synchronized void updateSettings(LifecycleConfig.BridgeSettings newSettings) {
        this.settings = newSettings;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(newSettings.timeoutSeconds()))
                .build();
    }

    public CompletableFuture<Boolean> startServer(String serverName) {
        return startServer(serverName, settings.resolveUnitName(serverName));
    }

    public CompletableFuture<Boolean> startServer(String serverName, String unitName) {
        String hookName = settings.hooks().start();
        String url = buildUrl(hookName, unitName);

        HttpRequest request = createRequestBuilder(url)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Successfully triggered start hook '{}' for server '{}' (unit: '{}')",
                                hookName, serverName, unitName);
                        return true;
                    } else {
                        logger.error("Failed to trigger start hook '{}' for server '{}' (unit: '{}'): HTTP {} - {}",
                                hookName, serverName, unitName, response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error communicating with systemd bridge while starting '{}' (unit: '{}'): {}",
                            serverName, unitName, ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> stopServer(String serverName) {
        return stopServer(serverName, settings.resolveUnitName(serverName));
    }

    public CompletableFuture<Boolean> stopServer(String serverName, String unitName) {
        String hookName = settings.hooks().stop();
        String url = buildUrl(hookName, unitName);

        HttpRequest request = createRequestBuilder(url)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Successfully triggered stop hook '{}' for server '{}' (unit: '{}')",
                                hookName, serverName, unitName);
                        return true;
                    } else {
                        logger.error("Failed to trigger stop hook '{}' for server '{}' (unit: '{}'): HTTP {} - {}",
                                hookName, serverName, unitName, response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error communicating with systemd bridge while stopping '{}' (unit: '{}'): {}",
                            serverName, unitName, ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<String> getStatus(String serverName) {
        return getStatus(serverName, settings.resolveUnitName(serverName));
    }

    public CompletableFuture<String> getStatus(String serverName, String unitName) {
        String hookName = settings.hooks().status();
        String url = buildUrl(hookName, unitName);

        HttpRequest request = createRequestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body() != null ? response.body().trim() : "";
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return body;
                    }
                    // Handle systemctl is-active non-zero exit code (exit 3 = inactive)
                    // If webhook returns HTTP 500 but body contains the systemctl output:
                    if (body.equalsIgnoreCase("inactive") || body.equalsIgnoreCase("failed") || body.equalsIgnoreCase("deactivating")) {
                        return body.toLowerCase();
                    }
                    if (body.contains("inactive")) {
                        return "inactive";
                    }
                    if (body.contains("active") && !body.contains("inactive")) {
                        return "active";
                    }
                    logger.warn("Bridge returned HTTP {} for server '{}' (unit '{}'): {}",
                            response.statusCode(), serverName, unitName, body);
                    return "unknown";
                })
                .exceptionally(ex -> {
                    logger.error("Error communicating with systemd bridge while getting status for '{}' (unit: '{}'): {}",
                            serverName, unitName, ex.getMessage());
                    return "unknown";
                });
    }

    private String buildUrl(String hookName, String unitName) {
        String base = settings.url();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return String.format("%s/hooks/%s?%s=%s",
                base,
                URLEncoder.encode(hookName, StandardCharsets.UTF_8),
                URLEncoder.encode(settings.parameterName(), StandardCharsets.UTF_8),
                URLEncoder.encode(unitName, StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder createRequestBuilder(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()));

        String token = settings.token();
        if (token != null && !token.isBlank()) {
            builder.header("X-Bridge-Token", token);
        }
        return builder;
    }
}
