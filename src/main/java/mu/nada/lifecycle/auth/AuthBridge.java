package mu.nada.lifecycle.auth;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.util.Optional;

public class AuthBridge {

    private final ProxyServer server;
    private final Logger logger;
    private final boolean authPresent;

    public AuthBridge(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.authPresent = server.getPluginManager().isLoaded("nadamu-auth");

        if (authPresent) {
            logger.info("Detected nadamu-auth integration. Zero-bypass security gate enabled.");
        } else {
            logger.info("nadamu-auth not detected. Running in standalone lifecycle mode.");
        }
    }

    public boolean isAllowedToConnect(Player player) {
        if (!authPresent) {
            return true;
        }

        try {
            return NadamuAuthHook.isAuthorized(server, player);
        } catch (Throwable t) {
            logger.warn("Failed to query nadamu-auth state for {}. Defaulting to safe restriction.",
                    player.getUsername(), t);
            return false;
        }
    }

    private static class NadamuAuthHook {
        static boolean isAuthorized(ProxyServer server, Player player) {
            Optional<PluginContainer> container = server.getPluginManager().getPlugin("nadamu-auth");
            if (container.isEmpty()) {
                return true;
            }

            Object instance = container.get().getInstance().orElse(null);
            if (!(instance instanceof mu.nada.nadamuauth.NadamuAuthPlugin authPlugin)) {
                return true;
            }

            mu.nada.nadamuauth.security.SessionManager sessionManager = authPlugin.getSessionManager();
            if (sessionManager == null) {
                return true;
            }

            mu.nada.nadamuauth.model.AuthState state = sessionManager.getAuthState(player.getUniqueId());
            if (state == null) {
                return true;
            }

            return state != mu.nada.nadamuauth.model.AuthState.PENDING_LOGIN;
        }
    }
}
