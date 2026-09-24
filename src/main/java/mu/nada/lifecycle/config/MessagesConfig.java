package mu.nada.lifecycle.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MessagesConfig {

    @Comment("Префикс сообщений плагина")
    private String prefix = "<dark_gray>[<gradient:#ffaa00:#ff5500>lifecycle</gradient>]</dark_gray> ";

    @Comment("Заголовок (Title) при запуске сервера. Разделитель <newline> делит Title и Subtitle")
    private String serverStarting = "<gold><b>Сервер <yellow>{server}</yellow> запускается...</b></gold><newline><gray>Пожалуйста, подождите немного</gray>";

    @Comment("Сообщение в Actionbar во время ожидания запуска")
    private String actionbarStarting = "<gold>Запуск <b>{server}</b>...</gold>";

    @Comment("Сообщение в Actionbar, когда сервер готов и происходит подключение")
    private String serverReady = "<green>Сервер {server} готов! Подключение...</green>";

    @Comment("Сообщение при ошибке запуска сервера по таймауту")
    private String serverFailed = "<red>Не удалось запустить сервер {server}. Пожалуйста, обратитесь к администратору.</red>";

    @Comment("Сообщение при отсутствии прав")
    private String noPermission = "<red>У вас нет прав на использование этой команды!</red>";

    @Comment("Заголовок списка статусов серверов")
    private String statusHeader = "<gold>--- [ <yellow>lifecycle: Статус серверов</yellow> ] ---</gold>";

    @Comment("Формат строки статуса одного сервера")
    private String statusLine = "<gray>- <white><b>{server}</b></white>: {color}{state}</color> <dark_gray>| Онлайн: <white>{online}</white> | Очередь: <white>{queued}</white></dark_gray>";

    @Comment("Успешная перезагрузка конфигурации")
    private String reloadSuccess = "<green>Конфигурация lifecycle и локализации успешно перезагружены!</green>";

    @Comment("Ошибка перезагрузки конфигурации")
    private String reloadError = "<red>Ошибка при перезагрузке конфигурации: {error}</red>";

    @Comment("Сервер не найден в реестре управляемых")
    private String serverNotFound = "<red>Сервер '{server}' не найден в управляемых!</red>";

    @Comment("Сигнал запуска отправлен")
    private String startInitiated = "<yellow>Отправлен сигнал запуска для {server}...</yellow>";

    @Comment("Сигнал остановки отправлен")
    private String stopInitiated = "<yellow>Отправлен сигнал остановки для {server}...</yellow>";

    @Comment("Сервер успешно остановлен")
    private String stopSuccess = "<green>Сервер {server} успешно остановлен.</green>";

    @Comment("Не удалось остановить сервер")
    private String stopFailed = "<red>Не удалось остановить сервер {server}!</red>";

    @Comment("Справка по командам")
    private String commandHelp = "<gold>--- [ <yellow>lifecycle Commands</yellow> ] ---</gold><newline>"
            + "<yellow>/lifecycle status</yellow> <gray>- Просмотр состояния серверов</gray><newline>"
            + "<yellow>/lifecycle start <server></yellow> <gray>- Принудительный запуск сервера</gray><newline>"
            + "<yellow>/lifecycle stop <server></yellow> <gray>- Принудительная остановка сервера</gray><newline>"
            + "<yellow>/lifecycle reload</yellow> <gray>- Перезагрузка конфигурации</gray>";

    @Comment("Использование команды start")
    private String usageStart = "<red>Использование: /lifecycle start <server></red>";

    @Comment("Использование команды stop")
    private String usageStop = "<red>Использование: /lifecycle stop <server></red>";

    public MessagesConfig() {
    }

    public static MessagesConfig createEnglishDefault() {
        MessagesConfig cfg = new MessagesConfig();
        cfg.prefix = "<dark_gray>[<gradient:#ffaa00:#ff5500>lifecycle</gradient>]</dark_gray> ";
        cfg.serverStarting = "<gold><b>Server <yellow>{server}</yellow> is starting...</b></gold><newline><gray>Please wait a moment</gray>";
        cfg.actionbarStarting = "<gold>Starting <b>{server}</b>...</gold>";
        cfg.serverReady = "<green>Server {server} is ready! Connecting...</green>";
        cfg.serverFailed = "<red>Failed to start server {server}. Please contact an administrator.</red>";
        cfg.noPermission = "<red>You do not have permission to execute this command!</red>";
        cfg.statusHeader = "<gold>--- [ <yellow>lifecycle: Server Status</yellow> ] ---</gold>";
        cfg.statusLine = "<gray>- <white><b>{server}</b></white>: {color}{state}</color> <dark_gray>| Online: <white>{online}</white> | Queue: <white>{queued}</white></dark_gray>";
        cfg.reloadSuccess = "<green>lifecycle configuration and localizations reloaded successfully!</green>";
        cfg.reloadError = "<red>Failed to reload configuration: {error}</red>";
        cfg.serverNotFound = "<red>Server '{server}' is not managed!</red>";
        cfg.startInitiated = "<yellow>Sent start signal for {server}...</yellow>";
        cfg.stopInitiated = "<yellow>Sent stop signal for {server}...</yellow>";
        cfg.stopSuccess = "<green>Server {server} stopped successfully.</green>";
        cfg.stopFailed = "<red>Failed to stop server {server}!</red>";
        cfg.commandHelp = "<gold>--- [ <yellow>lifecycle Commands</yellow> ] ---</gold><newline>"
                + "<yellow>/lifecycle status</yellow> <gray>- View servers state</gray><newline>"
                + "<yellow>/lifecycle start <server></yellow> <gray>- Force start server</gray><newline>"
                + "<yellow>/lifecycle stop <server></yellow> <gray>- Force stop server</gray><newline>"
                + "<yellow>/lifecycle reload</yellow> <gray>- Reload configuration</gray>";
        cfg.usageStart = "<red>Usage: /lifecycle start <server></red>";
        cfg.usageStop = "<red>Usage: /lifecycle stop <server></red>";
        return cfg;
    }

    public String prefix() {
        return prefix;
    }

    public String serverStarting() {
        return serverStarting;
    }

    public String actionbarStarting() {
        return actionbarStarting;
    }

    public String serverReady() {
        return serverReady;
    }

    public String serverFailed() {
        return serverFailed;
    }

    public String noPermission() {
        return noPermission;
    }

    public String statusHeader() {
        return statusHeader;
    }

    public String statusLine() {
        return statusLine;
    }

    public String reloadSuccess() {
        return reloadSuccess;
    }

    public String reloadError() {
        return reloadError;
    }

    public String serverNotFound() {
        return serverNotFound;
    }

    public String startInitiated() {
        return startInitiated;
    }

    public String stopInitiated() {
        return stopInitiated;
    }

    public String stopSuccess() {
        return stopSuccess;
    }

    public String stopFailed() {
        return stopFailed;
    }

    public String commandHelp() {
        return commandHelp;
    }

    public String usageStart() {
        return usageStart;
    }

    public String usageStop() {
        return usageStop;
    }
}
