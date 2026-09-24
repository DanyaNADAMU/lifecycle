package mu.nada.nadamulifecycle.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import mu.nada.nadamulifecycle.auth.AuthBridge;
import mu.nada.nadamulifecycle.client.SystemdBridgeClient;
import mu.nada.nadamulifecycle.config.LifecycleConfig;
import mu.nada.nadamulifecycle.model.ManagedServer;
import mu.nada.nadamulifecycle.model.ServerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WakeService {

    private final Object plugin;
    private final ProxyServer proxyServer;
    private final ServerRegistry registry;
    private final SystemdBridgeClient bridgeClient;
    private final AuthBridge authBridge;
    private final LifecycleConfig config;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private final Map<String, ScheduledTask> pollTasks = new ConcurrentHashMap<>();

    public WakeService(Object plugin,
                       ProxyServer proxyServer,
                       ServerRegistry registry,
                       SystemdBridgeClient bridgeClient,
                       AuthBridge authBridge,
                       LifecycleConfig config,
                       Logger logger) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.registry = registry;
        this.bridgeClient = bridgeClient;
        this.authBridge = authBridge;
        this.config = config;
        this.logger = logger;
    }

    public synchronized void startWarmup(String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            return;
        }

        ManagedServer server = optServer.get();
        if (server.state() == ServerState.RUNNING || server.state() == ServerState.STARTING) {
            return;
        }

        server.setState(ServerState.STARTING);
        server.setStartedAt(Instant.now());
        server.setEmptySince(null);

        logger.info("Initiating warmup for server '{}' via systemd bridge...", serverName);
        bridgeClient.startServer(server.name());

        startPollingTask(server);
    }

    public void queuePlayer(Player player, String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            return;
        }

        ManagedServer server = optServer.get();
        server.addQueuedPlayer(player.getUniqueId());

        startWarmup(serverName);
        sendStartingFeedback(player, server.name());
    }

    private void startPollingTask(ManagedServer server) {
        if (pollTasks.containsKey(server.name())) {
            return;
        }

        int interval = server.settings().pollIntervalSeconds();
        int maxWait = server.settings().maxStartupWaitSeconds();

        ScheduledTask task = proxyServer.getScheduler().buildTask(plugin, () -> {
            Instant started = server.startedAt();
            if (started != null && Duration.between(started, Instant.now()).toSeconds() > maxWait) {
                handleStartupTimeout(server);
                return;
            }

            server.registeredServer().ping().whenComplete((serverPing, throwable) -> {
                if (throwable == null && serverPing != null) {
                    handleStartupSuccess(server);
                } else {
                    for (UUID uuid : server.queuedPlayers()) {
                        proxyServer.getPlayer(uuid).ifPresent(p -> sendStartingFeedback(p, server.name()));
                    }
                }
            });
        }).repeat(Duration.ofSeconds(interval)).schedule();

        pollTasks.put(server.name(), task);
    }

    private synchronized void handleStartupSuccess(ManagedServer server) {
        ScheduledTask task = pollTasks.remove(server.name());
        if (task != null) {
            task.cancel();
        }

        server.setState(ServerState.RUNNING);
        server.setEmptySince(null);
        logger.info("Server '{}' is now healthy and accepting connections!", server.name());

        Component readyMsg = miniMessage.deserialize(
                config.messages().serverReady().replace("{server}", server.name()));

        for (UUID uuid : server.queuedPlayers()) {
            proxyServer.getPlayer(uuid).ifPresent(player -> {
                if (!authBridge.isAllowedToConnect(player)) {
                    logger.warn("Prevented early transfer of unauthenticated player {} to {}",
                            player.getUsername(), server.name());
                    return;
                }

                player.sendActionBar(readyMsg);
                player.createConnectionRequest(server.registeredServer()).connectWithIndication();
            });
        }

        server.clearQueuedPlayers();
    }

    private synchronized void handleStartupTimeout(ManagedServer server) {
        ScheduledTask task = pollTasks.remove(server.name());
        if (task != null) {
            task.cancel();
        }

        server.setState(ServerState.STOPPED);
        logger.error("Server '{}' failed to become ready within timeout ({}s)!",
                server.name(), server.settings().maxStartupWaitSeconds());

        Component failMsg = miniMessage.deserialize(
                config.messages().serverFailed().replace("{server}", server.name()));

        for (UUID uuid : server.queuedPlayers()) {
            proxyServer.getPlayer(uuid).ifPresent(player -> player.sendMessage(failMsg));
        }

        server.clearQueuedPlayers();
    }

    public void removeQueuedPlayer(UUID uuid) {
        for (ManagedServer server : registry.all()) {
            server.removeQueuedPlayer(uuid);
        }
    }

    private void sendStartingFeedback(Player player, String serverName) {
        String rawTemplate = config.messages().serverStarting().replace("{server}", serverName);
        String[] parts = rawTemplate.split("<newline>", 2);

        Component titleText = miniMessage.deserialize(parts[0]);
        Component subTitleText = parts.length > 1 ? miniMessage.deserialize(parts[1]) : Component.empty();

        Title title = Title.title(titleText, subTitleText, Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
        ));

        player.showTitle(title);
        player.sendActionBar(miniMessage.deserialize("<gold>Запуск <b>" + serverName + "</b>...</gold>"));
    }

    public void cancelAll() {
        for (ScheduledTask task : pollTasks.values()) {
            task.cancel();
        }
        pollTasks.clear();
    }
}
