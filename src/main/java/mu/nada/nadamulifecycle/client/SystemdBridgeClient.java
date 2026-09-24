package mu.nada.nadamulifecycle.client;

import mu.nada.nadamulifecycle.config.LifecycleConfig;
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

    private final HttpClient httpClient;
    private final LifecycleConfig.BridgeSettings settings;
    private final Logger logger;

    public SystemdBridgeClient(LifecycleConfig.BridgeSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .build();
    }

    public CompletableFuture<Boolean> startServer(String serverName) {
        String url = String.format("%s/hooks/start-server?server=%s",
                settings.url(),
                URLEncoder.encode(serverName, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Successfully triggered start for server '{}'", serverName);
                        return true;
                    } else {
                        logger.error("Failed to trigger start for server '{}': HTTP {} - {}",
                                serverName, response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error communicating with systemd bridge while starting '{}': {}",
                            serverName, ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> stopServer(String serverName) {
        String url = String.format("%s/hooks/stop-server?server=%s",
                settings.url(),
                URLEncoder.encode(serverName, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        logger.info("Successfully triggered stop for server '{}'", serverName);
                        return true;
                    } else {
                        logger.error("Failed to trigger stop for server '{}': HTTP {} - {}",
                                serverName, response.statusCode(), response.body());
                        return false;
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error communicating with systemd bridge while stopping '{}': {}",
                            serverName, ex.getMessage());
                    return false;
                });
    }

    public CompletableFuture<String> getStatus(String serverName) {
        String url = String.format("%s/hooks/server-status?server=%s",
                settings.url(),
                URLEncoder.encode(serverName, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body().trim();
                    } else {
                        return "unknown";
                    }
                })
                .exceptionally(ex -> "unknown");
    }
}
