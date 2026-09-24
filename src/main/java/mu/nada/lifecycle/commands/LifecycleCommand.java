package mu.nada.lifecycle.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import mu.nada.lifecycle.client.SystemdBridgeClient;
import mu.nada.lifecycle.config.ConfigManager;
import mu.nada.lifecycle.config.MessagesConfig;
import mu.nada.lifecycle.i18n.LanguageManager;
import mu.nada.lifecycle.model.ManagedServer;
import mu.nada.lifecycle.model.ServerState;
import mu.nada.lifecycle.service.ServerRegistry;
import mu.nada.lifecycle.service.WakeService;
import mu.nada.lifecycle.util.MessageService;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class LifecycleCommand implements SimpleCommand {

    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final ServerRegistry registry;
    private final WakeService wakeService;
    private final SystemdBridgeClient bridgeClient;
    private final MessageService messageService;
    private final Logger logger;

    public LifecycleCommand(ConfigManager configManager,
                            LanguageManager languageManager,
                            ServerRegistry registry,
                            WakeService wakeService,
                            SystemdBridgeClient bridgeClient,
                            MessageService messageService,
                            Logger logger) {
        this.configManager = configManager;
        this.languageManager = languageManager;
        this.registry = registry;
        this.wakeService = wakeService;
        this.bridgeClient = bridgeClient;
        this.messageService = messageService;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(source);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "status" -> handleStatus(source);
            case "reload" -> handleReload(source);
            case "start" -> {
                if (args.length < 2) {
                    messageService.sendMessage(source, MessagesConfig::usageStart);
                    return;
                }
                handleStart(source, args[1]);
            }
            case "stop" -> {
                if (args.length < 2) {
                    messageService.sendMessage(source, MessagesConfig::usageStop);
                    return;
                }
                handleStop(source, args[1]);
            }
            default -> sendHelp(source);
        }
    }

    private void handleStatus(CommandSource source) {
        messageService.sendRawMessage(source, messageService.getRawComponent(source, MessagesConfig::statusHeader));
        for (ManagedServer server : registry.all()) {
            int online = server.registeredServer().getPlayersConnected().size();
            String color = switch (server.state()) {
                case RUNNING -> "<green>";
                case STARTING -> "<yellow>";
                case STOPPING -> "<orange>";
                case STOPPED -> "<red>";
            };

            Component line = messageService.getRawComponent(source, MessagesConfig::statusLine, Map.of(
                    "server", server.name(),
                    "color", color,
                    "state", server.state().name(),
                    "online", String.valueOf(online),
                    "queued", String.valueOf(server.queuedPlayers().size())
            ));
            messageService.sendRawMessage(source, line);
        }
    }

    private void handleReload(CommandSource source) {
        try {
            configManager.reload();
            languageManager.reload(configManager.config().defaultLanguage());
            registry.load(configManager.config());
            bridgeClient.updateSettings(configManager.config().bridge());
            messageService.sendMessage(source, MessagesConfig::reloadSuccess);
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            messageService.sendMessage(source, MessagesConfig::reloadError,
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"));
        }
    }

    private void handleStart(CommandSource source, String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            messageService.sendMessage(source, MessagesConfig::serverNotFound, Map.of("server", serverName));
            return;
        }

        messageService.sendMessage(source, MessagesConfig::startInitiated, Map.of("server", serverName));
        wakeService.startWarmup(serverName);
    }

    private void handleStop(CommandSource source, String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            messageService.sendMessage(source, MessagesConfig::serverNotFound, Map.of("server", serverName));
            return;
        }

        ManagedServer server = optServer.get();
        server.setState(ServerState.STOPPING);
        messageService.sendMessage(source, MessagesConfig::stopInitiated, Map.of("server", serverName));

        bridgeClient.stopServer(serverName, server.unitName()).thenAccept(success -> {
            if (success) {
                server.setState(ServerState.STOPPED);
                messageService.sendMessage(source, MessagesConfig::stopSuccess, Map.of("server", serverName));
            } else {
                server.setState(ServerState.RUNNING);
                messageService.sendMessage(source, MessagesConfig::stopFailed, Map.of("server", serverName));
            }
        });
    }

    private void sendHelp(CommandSource source) {
        messageService.sendRawMessage(source, messageService.getRawComponent(source, MessagesConfig::commandHelp));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("nadamu.lifecycle.admin");
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            return List.of("status", "start", "stop", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            return registry.all().stream().map(ManagedServer::name).toList();
        }
        return List.of();
    }
}
