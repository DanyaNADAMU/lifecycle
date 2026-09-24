package mu.nada.nadamulifecycle.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import mu.nada.nadamulifecycle.service.ServerRegistry;
import mu.nada.nadamulifecycle.service.WakeService;
import org.slf4j.Logger;

public class InitialServerListener {

    private final ServerRegistry registry;
    private final WakeService wakeService;
    private final Logger logger;

    public InitialServerListener(ServerRegistry registry, WakeService wakeService, Logger logger) {
        this.registry = registry;
        this.wakeService = wakeService;
        this.logger = logger;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        event.getInitialServer().ifPresent(server -> {
            String name = server.getServerInfo().getName();
            if (registry.isManaged(name)) {
                logger.info("Forced-host / initial destination '{}' detected for player {}. Starting early warmup...",
                        name, event.getPlayer().getUsername());
                wakeService.startWarmup(name);
            }
        });
    }
}
