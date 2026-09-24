package mu.nada.lifecycle.service;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import mu.nada.lifecycle.config.LifecycleConfig;
import mu.nada.lifecycle.model.ManagedServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ServerRegistry {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Map<String, ManagedServer> servers = new ConcurrentHashMap<>();

    public ServerRegistry(ProxyServer proxyServer, Logger logger) {
        this.proxyServer = proxyServer;
        this.logger = logger;
    }

    public void load(LifecycleConfig config) {
        servers.clear();
        LifecycleConfig.ServerSettings defaults = config.defaults();
        String limbo = config.limboServer() != null ? config.limboServer().toLowerCase() : "limbo";

        if (config.auto()) {
            logger.info("Auto pool discovery enabled: scanning all servers in velocity.toml...");
            for (RegisteredServer registered : proxyServer.getAllServers()) {
                String name = registered.getServerInfo().getName();
                if (name.equalsIgnoreCase(limbo)) {
                    continue; // Technical fallback server, never scale to zero
                }

                LifecycleConfig.ServerSettings override = config.servers().get(name.toLowerCase());
                if (override != null && !override.isEnabled()) {
                    logger.info("Server '{}' is explicitly excluded from lifecycle management (enabled: false).", name);
                    continue;
                }

                LifecycleConfig.ServerSettings effective = defaults.mergeWith(override);
                String defaultUnit = config.bridge().resolveUnitName(name);
                ManagedServer server = new ManagedServer(name, registered, effective, defaultUnit);
                servers.put(name.toLowerCase(), server);
                logger.info("Registered managed lifecycle server (auto): '{}' (unit: '{}')", name, server.unitName());
            }
        } else {
            logger.info("Manual pool mode enabled: registering only explicitly listed servers...");
            for (Map.Entry<String, LifecycleConfig.ServerSettings> entry : config.servers().entrySet()) {
                String name = entry.getKey();
                LifecycleConfig.ServerSettings override = entry.getValue();

                if (name.equalsIgnoreCase(limbo)) {
                    logger.warn("Server '{}' is configured as limbo-server and cannot be managed by lifecycle!", name);
                    continue;
                }

                if (!override.isEnabled()) {
                    logger.info("Server '{}' is explicitly disabled in configuration.", name);
                    continue;
                }

                Optional<RegisteredServer> registered = proxyServer.getServer(name);
                if (registered.isPresent()) {
                    LifecycleConfig.ServerSettings effective = defaults.mergeWith(override);
                    String defaultUnit = config.bridge().resolveUnitName(name);
                    ManagedServer server = new ManagedServer(name, registered.get(), effective, defaultUnit);
                    servers.put(name.toLowerCase(), server);
                    logger.info("Registered managed lifecycle server (manual): '{}' (unit: '{}')", name, server.unitName());
                } else {
                    logger.warn("Server '{}' configured in lifecycle config.yml but NOT registered in velocity.toml!", name);
                }
            }
        }
    }

    public Optional<ManagedServer> get(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(servers.get(name.toLowerCase()));
    }

    public boolean isManaged(String name) {
        if (name == null) {
            return false;
        }
        return servers.containsKey(name.toLowerCase());
    }

    public Collection<ManagedServer> all() {
        return Collections.unmodifiableCollection(servers.values());
    }
}
