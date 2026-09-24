package mu.nada.lifecycle;

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
import mu.nada.lifecycle.auth.AuthBridge;
import mu.nada.lifecycle.client.SystemdBridgeClient;
import mu.nada.lifecycle.commands.LifecycleCommand;
import mu.nada.lifecycle.config.ConfigManager;
import mu.nada.lifecycle.config.LifecycleConfig;
import mu.nada.lifecycle.i18n.LanguageManager;
import mu.nada.lifecycle.listeners.DisconnectListener;
import mu.nada.lifecycle.listeners.InitialServerListener;
import mu.nada.lifecycle.listeners.PreConnectListener;
import mu.nada.lifecycle.service.IdleService;
import mu.nada.lifecycle.service.ServerRegistry;
import mu.nada.lifecycle.service.WakeService;
import mu.nada.lifecycle.util.MessageService;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

import java.nio.file.Path;

@Plugin(
        id = "lifecycle",
        name = "lifecycle",
        version = "1.0.0",
        authors = {"DanyaNADAMU"},
        description = "Scale-to-Zero and Server Lifecycle Manager for Velocity & Podman Quadlet",
        dependencies = {
                @Dependency(id = "nadamu-auth", optional = true)
        }
)
public class LifecyclePlugin {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private MessageService messageService;
    private SystemdBridgeClient bridgeClient;
    private ServerRegistry serverRegistry;
    private AuthBridge authBridge;
    private WakeService wakeService;
    private IdleService idleService;

    @Inject
    public LifecyclePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Initializing lifecycle...");

        // 1. Load configuration
        this.configManager = new ConfigManager(dataDirectory, logger);
        try {
            this.configManager.reload();
            logger.info("Configuration loaded successfully.");
        } catch (ConfigurateException e) {
            logger.error("Failed to load lifecycle configuration!", e);
            return;
        }

        LifecycleConfig config = configManager.config();

        // 2. Initialize i18n
        this.languageManager = new LanguageManager(dataDirectory, logger);
        try {
            this.languageManager.reload(config.defaultLanguage());
        } catch (ConfigurateException e) {
            logger.error("Failed to load language dictionaries!", e);
        }
        this.messageService = new MessageService(languageManager);

        // 3. Initialize components
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
                messageService,
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

        // 4. Register listeners
        EventManager eventManager = server.getEventManager();
        eventManager.register(this, new InitialServerListener(serverRegistry, wakeService, logger));
        eventManager.register(this, new PreConnectListener(server, serverRegistry, wakeService, authBridge, config, logger));
        eventManager.register(this, new DisconnectListener(wakeService));

        // 5. Register commands
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("lifecycle")
                .aliases("lc")
                .plugin(this)
                .build();

        commandManager.register(commandMeta, new LifecycleCommand(
                configManager,
                languageManager,
                serverRegistry,
                wakeService,
                bridgeClient,
                messageService,
                logger
        ));

        logger.info("lifecycle has been initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("Shutting down lifecycle...");

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

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public MessageService getMessageService() {
        return messageService;
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
