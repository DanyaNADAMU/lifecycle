package mu.nada.lifecycle.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import mu.nada.lifecycle.auth.AuthBridge;
import mu.nada.lifecycle.config.LifecycleConfig;
import mu.nada.lifecycle.model.ManagedServer;
import mu.nada.lifecycle.model.ServerState;
import mu.nada.lifecycle.service.ServerRegistry;
import mu.nada.lifecycle.service.WakeService;
import org.slf4j.Logger;

import java.util.Optional;

public class PreConnectListener {

    private final ProxyServer proxyServer;
    private final ServerRegistry registry;
    private final WakeService wakeService;
    private final AuthBridge authBridge;
    private final LifecycleConfig config;
    private final Logger logger;

    public PreConnectListener(ProxyServer proxyServer,
                              ServerRegistry registry,
                              WakeService wakeService,
                              AuthBridge authBridge,
                              LifecycleConfig config,
                              Logger logger) {
        this.proxyServer = proxyServer;
        this.registry = registry;
        this.wakeService = wakeService;
        this.authBridge = authBridge;
        this.config = config;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.EARLY)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        RegisteredServer target = event.getOriginalServer();
        String targetName = target.getServerInfo().getName();

        Optional<ManagedServer> optManaged = registry.get(targetName);
        if (optManaged.isEmpty()) {
            return;
        }

        ManagedServer managed = optManaged.get();
        if (managed.state() == ServerState.RUNNING) {
            return; // Server is ready, proceed normally
        }

        Player player = event.getPlayer();

        // Zero-Bypass Security Check:
        // If the player is still pending password login in nadamu-auth, do NOT queue them for gameplay transfer!
        if (!authBridge.isAllowedToConnect(player)) {
            logger.debug("Player {} attempted connection to {} while pending authentication. Ignored by lifecycle.",
                    player.getUsername(), targetName);
            return;
        }

        // Player is authorized (authenticated or guest), but server is not running yet
        String limboName = config.limboServer();
        boolean alreadyOnLimbo = player.getCurrentServer()
                .map(s -> s.getServerInfo().getName().equalsIgnoreCase(limboName))
                .orElse(false);

        if (!alreadyOnLimbo) {
            // Redirect player to limbo holding server
            proxyServer.getServer(limboName).ifPresent(limbo -> {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo));
            });
        } else {
            // Player is already on limbo; deny connection to avoid Velocity already-connected error
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        }

        // Add player to the wake queue and start/ensure warmup
        wakeService.queuePlayer(player, managed.name());
    }
}
