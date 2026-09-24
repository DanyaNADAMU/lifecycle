package mu.nada.nadamulifecycle.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import mu.nada.nadamulifecycle.service.WakeService;

public class DisconnectListener {

    private final WakeService wakeService;

    public DisconnectListener(WakeService wakeService) {
        this.wakeService = wakeService;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        wakeService.removeQueuedPlayer(event.getPlayer().getUniqueId());
    }
}
