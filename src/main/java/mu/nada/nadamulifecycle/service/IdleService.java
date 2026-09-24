package mu.nada.nadamulifecycle.service;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import mu.nada.nadamulifecycle.client.SystemdBridgeClient;
import mu.nada.nadamulifecycle.model.ManagedServer;
import mu.nada.nadamulifecycle.model.ServerState;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;

public class IdleService implements Runnable {

    private final Object plugin;
    private final ProxyServer proxyServer;
    private final ServerRegistry registry;
    private final SystemdBridgeClient bridgeClient;
    private final Logger logger;

    private ScheduledTask watchdogTask;

    public IdleService(Object plugin,
                       ProxyServer proxyServer,
                       ServerRegistry registry,
                       SystemdBridgeClient bridgeClient,
                       Logger logger) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.registry = registry;
        this.bridgeClient = bridgeClient;
        this.logger = logger;
    }

    public void start() {
        this.watchdogTask = proxyServer.getScheduler()
                .buildTask(plugin, this)
                .repeat(Duration.ofSeconds(20))
                .schedule();
    }

    public void stop() {
        if (watchdogTask != null) {
            watchdogTask.cancel();
        }
    }

    @Override
    public void run() {
        Instant now = Instant.now();

        for (ManagedServer server : registry.all()) {
            if (server.state() != ServerState.RUNNING) {
                continue;
            }

            int onlinePlayers = server.registeredServer().getPlayersConnected().size();

            if (onlinePlayers > 0) {
                server.setEmptySince(null);
                continue;
            }

            // Players online == 0
            Instant startedAt = server.startedAt();
            if (startedAt != null) {
                long secondsSinceStart = Duration.between(startedAt, now).toSeconds();
                if (secondsSinceStart < server.settings().startupGracePeriodSeconds()) {
                    // Still in initial grace period
                    continue;
                }
            }

            if (server.emptySince() == null) {
                server.setEmptySince(now);
                logger.info("Server '{}' is now empty. Initiating idle countdown...", server.name());
                continue;
            }

            long emptyMinutes = Duration.between(server.emptySince(), now).toMinutes();
            if (emptyMinutes >= server.settings().idleTimeoutMinutes()) {
                logger.info("Server '{}' has been empty for {} minutes (limit: {}). Triggering scale-to-zero stop...",
                        server.name(), emptyMinutes, server.settings().idleTimeoutMinutes());

                server.setState(ServerState.STOPPING);
                bridgeClient.stopServer(server.name()).thenAccept(success -> {
                    if (success) {
                        server.setState(ServerState.STOPPED);
                        server.setStartedAt(null);
                        server.setEmptySince(null);
                        logger.info("Server '{}' stopped successfully.", server.name());
                    } else {
                        server.setState(ServerState.RUNNING); // revert if failed
                        logger.error("Failed to stop server '{}'. Reverting state to RUNNING.", server.name());
                    }
                });
            }
        }
    }
}
