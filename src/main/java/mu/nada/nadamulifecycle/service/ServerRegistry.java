package mu.nada.nadamulifecycle.service;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import mu.nada.nadamulifecycle.config.LifecycleConfig;
import mu.nada.nadamulifecycle.model.ManagedServer;
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

        for (Map.Entry<String, LifecycleConfig.ServerSettings> entry : config.servers().entrySet()) {
            String name = entry.getKey();
            LifecycleConfig.ServerSettings settings = entry.getValue();

            Optional<RegisteredServer> registered = proxyServer.getServer(name);
            if (registered.isPresent()) {
                servers.put(name.toLowerCase(), new ManagedServer(name, registered.get(), settings));
                logger.info("Registered managed lifecycle server: '{}'", name);
            } else {
                logger.warn("Server '{}' configured in lifecycle config.yml but NOT registered in velocity.toml!", name);
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
