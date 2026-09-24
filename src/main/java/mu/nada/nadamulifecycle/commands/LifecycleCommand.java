package mu.nada.nadamulifecycle.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import mu.nada.nadamulifecycle.client.SystemdBridgeClient;
import mu.nada.nadamulifecycle.config.ConfigManager;
import mu.nada.nadamulifecycle.model.ManagedServer;
import mu.nada.nadamulifecycle.model.ServerState;
import mu.nada.nadamulifecycle.service.ServerRegistry;
import mu.nada.nadamulifecycle.service.WakeService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public class LifecycleCommand implements SimpleCommand {

    private final ConfigManager configManager;
    private final ServerRegistry registry;
    private final WakeService wakeService;
    private final SystemdBridgeClient bridgeClient;
    private final Logger logger;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LifecycleCommand(ConfigManager configManager,
                            ServerRegistry registry,
                            WakeService wakeService,
                            SystemdBridgeClient bridgeClient,
                            Logger logger) {
        this.configManager = configManager;
        this.registry = registry;
        this.wakeService = wakeService;
        this.bridgeClient = bridgeClient;
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
                    source.sendMessage(miniMessage.deserialize("<red>Использование: /lifecycle start <server></red>"));
                    return;
                }
                handleStart(source, args[1]);
            }
            case "stop" -> {
                if (args.length < 2) {
                    source.sendMessage(miniMessage.deserialize("<red>Использование: /lifecycle stop <server></red>"));
                    return;
                }
                handleStop(source, args[1]);
            }
            default -> sendHelp(source);
        }
    }

    private void handleStatus(CommandSource source) {
        source.sendMessage(miniMessage.deserialize("<gold>--- [ <yellow>nadamu-lifecycle: Статус серверов</yellow> ] ---</gold>"));
        for (ManagedServer server : registry.all()) {
            int online = server.registeredServer().getPlayersConnected().size();
            String color = switch (server.state()) {
                case RUNNING -> "<green>";
                case STARTING -> "<yellow>";
                case STOPPING -> "<orange>";
                case STOPPED -> "<red>";
            };

            String line = String.format("<gray>- <white><b>%s</b></white>: %s%s</color> <dark_gray>| Онлайн: <white>%d</white> | Очередь: <white>%d</white></dark_gray>",
                    server.name(), color, server.state(), online, server.queuedPlayers().size());
            source.sendMessage(miniMessage.deserialize(line));
        }
    }

    private void handleReload(CommandSource source) {
        try {
            configManager.reload();
            registry.load(configManager.config());
            source.sendMessage(miniMessage.deserialize("<green>Конфигурация nadamu-lifecycle успешно перезагружена!</green>"));
        } catch (Exception e) {
            logger.error("Failed to reload configuration", e);
            source.sendMessage(miniMessage.deserialize("<red>Ошибка при перезагрузке конфига: " + e.getMessage() + "</red>"));
        }
    }

    private void handleStart(CommandSource source, String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            source.sendMessage(miniMessage.deserialize("<red>Сервер '" + serverName + "' не найден в управляемых!</red>"));
            return;
        }

        source.sendMessage(miniMessage.deserialize("<yellow>Отправлен сигнал запуска для " + serverName + "...</yellow>"));
        wakeService.startWarmup(serverName);
    }

    private void handleStop(CommandSource source, String serverName) {
        Optional<ManagedServer> optServer = registry.get(serverName);
        if (optServer.isEmpty()) {
            source.sendMessage(miniMessage.deserialize("<red>Сервер '" + serverName + "' не найден в управляемых!</red>"));
            return;
        }

        ManagedServer server = optServer.get();
        server.setState(ServerState.STOPPING);
        source.sendMessage(miniMessage.deserialize("<yellow>Отправлен сигнал остановки для " + serverName + "...</yellow>"));

        bridgeClient.stopServer(serverName).thenAccept(success -> {
            if (success) {
                server.setState(ServerState.STOPPED);
                source.sendMessage(miniMessage.deserialize("<green>Сервер " + serverName + " успешно остановлен.</green>"));
            } else {
                server.setState(ServerState.RUNNING);
                source.sendMessage(miniMessage.deserialize("<red>Не удалось остановить сервер " + serverName + "!</red>"));
            }
        });
    }

    private void sendHelp(CommandSource source) {
        source.sendMessage(miniMessage.deserialize("<gold>--- [ <yellow>nadamu-lifecycle Commands</yellow> ] ---</gold>"));
        source.sendMessage(miniMessage.deserialize("<yellow>/lifecycle status</yellow> <gray>- Просмотр состояния серверов</gray>"));
        source.sendMessage(miniMessage.deserialize("<yellow>/lifecycle start <server></yellow> <gray>- Принудительный запуск сервера</gray>"));
        source.sendMessage(miniMessage.deserialize("<yellow>/lifecycle stop <server></yellow> <gray>- Принудительная остановка сервера</gray>"));
        source.sendMessage(miniMessage.deserialize("<yellow>/lifecycle reload</yellow> <gray>- Перезагрузка конфигурации</gray>"));
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
