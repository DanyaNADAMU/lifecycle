package mu.nada.lifecycle.service;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import mu.nada.lifecycle.auth.AuthBridge;
import mu.nada.lifecycle.client.SystemdBridgeClient;
import mu.nada.lifecycle.config.MessagesConfig;
import mu.nada.lifecycle.model.ManagedServer;
import mu.nada.lifecycle.model.ServerState;
import mu.nada.lifecycle.util.MessageService;
import net.kyori.adventure.text.Component;
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
    private final MessageService messageService;
    private final Logger logger;

    private final Map<String, ScheduledTask> pollTasks = new ConcurrentHashMap<>();

    public WakeService(Object plugin,
                       ProxyServer proxyServer,
                       ServerRegistry registry,
                       SystemdBridgeClient bridgeClient,
                       AuthBridge authBridge,
                       MessageService messageService,
                       Logger logger) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.registry = registry;
        this.bridgeClient = bridgeClient;
        this.authBridge = authBridge;
        this.messageService = messageService;
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

        logger.info("Initiating warmup for server '{}' (unit: '{}') via systemd bridge...", serverName, server.unitName());
        bridgeClient.startServer(server.name(), server.unitName());

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

        for (UUID uuid : server.queuedPlayers()) {
            proxyServer.getPlayer(uuid).ifPresent(player -> {
                if (!authBridge.isAllowedToConnect(player)) {
                    logger.warn("Prevented early transfer of unauthenticated player {} to {}",
                            player.getUsername(), server.name());
                    return;
                }

                Component readyMsg = messageService.getRawComponent(player, MessagesConfig::serverReady,
                        Map.of("server", server.name()));
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

        for (UUID uuid : server.queuedPlayers()) {
            proxyServer.getPlayer(uuid).ifPresent(player ->
                    messageService.sendMessage(player, MessagesConfig::serverFailed, Map.of("server", server.name())));
        }

        server.clearQueuedPlayers();
    }

    public void removeQueuedPlayer(UUID uuid) {
        for (ManagedServer server : registry.all()) {
            server.removeQueuedPlayer(uuid);
        }
    }

    private void sendStartingFeedback(Player player, String serverName) {
        MessagesConfig msgs = messageService.getMessages(player);
        String rawTemplate = msgs.serverStarting().replace("{server}", serverName);
        String[] parts = rawTemplate.split("<newline>", 2);

        Component titleText = messageService.parse(parts[0]);
        Component subTitleText = parts.length > 1 ? messageService.parse(parts[1]) : Component.empty();

        Title title = Title.title(titleText, subTitleText, Title.Times.times(
                Duration.ofMillis(300),
                Duration.ofSeconds(2),
                Duration.ofMillis(500)
        ));

        player.showTitle(title);
        Component actionbar = messageService.getRawComponent(player, MessagesConfig::actionbarStarting,
                Map.of("server", serverName));
        player.sendActionBar(actionbar);
    }

    public void cancelAll() {
        for (ScheduledTask task : pollTasks.values()) {
            task.cancel();
        }
        pollTasks.clear();
    }
}
