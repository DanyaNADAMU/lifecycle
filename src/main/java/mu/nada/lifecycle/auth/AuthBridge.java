package mu.nada.lifecycle.auth;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

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
        private static volatile Method getSessionManagerMethod;
        private static volatile Method getAuthStateMethod;
        private static volatile boolean initialized = false;

        static boolean isAuthorized(ProxyServer server, Player player) {
            Optional<PluginContainer> container = server.getPluginManager().getPlugin("nadamu-auth");
            if (container.isEmpty()) {
                return true;
            }

            Object authPlugin = container.get().getInstance().orElse(null);
            if (authPlugin == null) {
                return true;
            }

            try {
                if (!initialized) {
                    synchronized (NadamuAuthHook.class) {
                        if (!initialized) {
                            getSessionManagerMethod = authPlugin.getClass().getMethod("getSessionManager");
                            Object sessionManager = getSessionManagerMethod.invoke(authPlugin);
                            if (sessionManager != null) {
                                getAuthStateMethod = sessionManager.getClass().getMethod("getAuthState", UUID.class);
                            }
                            initialized = true;
                        }
                    }
                }

                if (getSessionManagerMethod == null) {
                    return true;
                }

                Object sessionManager = getSessionManagerMethod.invoke(authPlugin);
                if (sessionManager == null || getAuthStateMethod == null) {
                    return true;
                }

                Object stateObj = getAuthStateMethod.invoke(sessionManager, player.getUniqueId());
                if (stateObj == null) {
                    return true;
                }

                String stateName = stateObj.toString();
                return !"PENDING_LOGIN".equalsIgnoreCase(stateName);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
