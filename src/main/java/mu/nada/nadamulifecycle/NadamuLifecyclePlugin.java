package mu.nada.nadamulifecycle;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import mu.nada.nadamulifecycle.auth.AuthBridge;
import mu.nada.nadamulifecycle.client.SystemdBridgeClient;
import mu.nada.nadamulifecycle.commands.LifecycleCommand;
import mu.nada.nadamulifecycle.config.ConfigManager;
import mu.nada.nadamulifecycle.config.LifecycleConfig;
import mu.nada.nadamulifecycle.listeners.DisconnectListener;
import mu.nada.nadamulifecycle.listeners.InitialServerListener;
import mu.nada.nadamulifecycle.listeners.PreConnectListener;
import mu.nada.nadamulifecycle.service.IdleService;
import mu.nada.nadamulifecycle.service.ServerRegistry;
import mu.nada.nadamulifecycle.service.WakeService;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

import java.nio.file.Path;

@Plugin(
        id = "nadamu-lifecycle",
        name = "nadamu-lifecycle",
        version = "1.0.0",
        authors = {"DanyaNADAMU"},
        description = "Scale-to-Zero and Server Lifecycle Manager for Velocity & Podman Quadlet",
        dependencies = {
                @Dependency(id = "nadamu-auth", optional = true)
        }
)
public class NadamuLifecyclePlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private SystemdBridgeClient bridgeClient;
    private ServerRegistry serverRegistry;
    private AuthBridge authBridge;
    private WakeService wakeService;
    private IdleService idleService;

    @Inject
    public NadamuLifecyclePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing nadamu-lifecycle...");

        // 1. Load configuration
        this.configManager = new ConfigManager(dataDirectory, logger);
        try {
            this.configManager.reload();
            logger.info("Configuration loaded successfully.");
        } catch (ConfigurateException e) {
            logger.error("Failed to load nadamu-lifecycle configuration!", e);
            return;
        }

        LifecycleConfig config = configManager.config();

        // 2. Initialize components
        this.bridgeClient = new SystemdBridgeClient(config.bridge(), logger);
        this.serverRegistry = new ServerRegistry(server, logger);
        this.serverRegistry.load(config);
        this.authBridge = new AuthBridge(server, logger);

        this.wakeService = new WakeService(
                this,
                server,
                serverRegistry,
                bridgeClient,
                authBridge,
                config,
                logger
        );

        this.idleService = new IdleService(
                this,
                server,
                serverRegistry,
                bridgeClient,
                logger
        );
        this.idleService.start();

        // 3. Register listeners
        EventManager eventManager = server.getEventManager();
        eventManager.register(this, new InitialServerListener(serverRegistry, wakeService, logger));
        eventManager.register(this, new PreConnectListener(server, serverRegistry, wakeService, authBridge, config, logger));
        eventManager.register(this, new DisconnectListener(wakeService));

        // 4. Register commands
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("lifecycle")
                .aliases("nlc", "nl")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new LifecycleCommand(
                configManager,
                serverRegistry,
                wakeService,
                bridgeClient,
                logger
        ));

        logger.info("nadamu-lifecycle has been initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down nadamu-lifecycle...");

        if (idleService != null) {
            idleService.stop();
        }

        if (wakeService != null) {
            wakeService.cancelAll();
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ServerRegistry getServerRegistry() {
        return serverRegistry;
    }

    public WakeService getWakeService() {
        return wakeService;
    }

    public IdleService getIdleService() {
        return idleService;
    }
}
