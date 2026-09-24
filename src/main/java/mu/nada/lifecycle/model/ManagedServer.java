package mu.nada.lifecycle.model;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import mu.nada.lifecycle.config.LifecycleConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ManagedServer {

    private final String name;
    private final String unitName;
    private final RegisteredServer registeredServer;
    private final LifecycleConfig.ServerSettings settings;

    private volatile ServerState state = ServerState.STOPPED;
    private volatile Instant startedAt;
    private volatile Instant emptySince;

    private final Set<UUID> queuedPlayers = ConcurrentHashMap.newKeySet();

    public ManagedServer(String name, RegisteredServer registeredServer, LifecycleConfig.ServerSettings settings) {
        this(name, registeredServer, settings, "mc@" + name);
    }

    public ManagedServer(String name, RegisteredServer registeredServer, LifecycleConfig.ServerSettings settings, String defaultUnit) {
        this.name = name;
        this.registeredServer = registeredServer;
        this.settings = settings;
        if (settings.unit() != null && !settings.unit().isBlank()) {
            this.unitName = settings.unit();
        } else if (defaultUnit != null && !defaultUnit.isBlank()) {
            this.unitName = defaultUnit;
        } else {
            this.unitName = "mc@" + name;
        }
    }

    public String name() {
        return name;
    }

    public String unitName() {
        return unitName;
    }

    public RegisteredServer registeredServer() {
        return registeredServer;
    }

    public LifecycleConfig.ServerSettings settings() {
        return settings;
    }

    public ServerState state() {
        return state;
    }

    public void setState(ServerState state) {
        this.state = state;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant emptySince() {
        return emptySince;
    }

    public void setEmptySince(Instant emptySince) {
        this.emptySince = emptySince;
    }

    public Set<UUID> queuedPlayers() {
        return Collections.unmodifiableSet(queuedPlayers);
    }

    public void addQueuedPlayer(UUID uuid) {
        queuedPlayers.add(uuid);
    }

    public void removeQueuedPlayer(UUID uuid) {
        queuedPlayers.remove(uuid);
    }

    public void clearQueuedPlayers() {
        queuedPlayers.clear();
    }
}
