package mu.nada.lifecycle.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.HashMap;
import java.util.Map;

@ConfigSerializable
public class LifecycleConfig {

    @Comment("Configuration for the host-level systemd webhook bridge")
    private BridgeSettings bridge = new BridgeSettings();

    @Comment("Fallback server used to hold players while backend servers are booting up")
    private String limboServer = "limbo";

    @Comment("Player notifications displayed during server startup (MiniMessage format)")
    private MessageSettings messages = new MessageSettings();

    @Comment("Managed backend servers mapped to their individual lifecycle configurations")
    private Map<String, ServerSettings> servers = new HashMap<>(Map.of(
            "pvp", new ServerSettings(10, 180, 2, 120),
            "pillar", new ServerSettings(10, 180, 2, 120),
            "forge", new ServerSettings(15, 300, 3, 300)
    ));

    public BridgeSettings bridge() {
        return bridge;
    }

    public String limboServer() {
        return limboServer;
    }

    public MessageSettings messages() {
        return messages;
    }

    public Map<String, ServerSettings> servers() {
        return servers;
    }

    @ConfigSerializable
    public static class BridgeSettings {
        @Comment("URL of the host webhook bridge")
        private String url = "http://host.containers.internal:9000";

        @Comment("Timeout in seconds for webhook HTTP requests")
        private int timeoutSeconds = 5;

        public String url() {
            return url;
        }

        public int timeoutSeconds() {
            return timeoutSeconds;
        }
    }

    @ConfigSerializable
    public static class MessageSettings {
        private String serverStarting = "<gold><b>Сервер <yellow>{server}</yellow> запускается...</b></gold><newline><gray>Пожалуйста, подождите немного</gray>";
        private String serverReady = "<green>Сервер {server} готов! Подключение...</green>";
        private String serverFailed = "<red>Не удалось запустить сервер {server}. Пожалуйста, обратитесь к администратору.</red>";

        public String serverStarting() {
            return serverStarting;
        }

        public String serverReady() {
            return serverReady;
        }

        public String serverFailed() {
            return serverFailed;
        }
    }

    @ConfigSerializable
    public static class ServerSettings {
        @Comment("How long the server can stay completely empty before being stopped (in minutes)")
        private int idleTimeoutMinutes = 10;

        @Comment("Minimum time after boot before idle shutdown checks are enforced (in seconds)")
        private int startupGracePeriodSeconds = 180;

        @Comment("Interval between TCP pings while checking if server is alive (in seconds)")
        private int pollIntervalSeconds = 2;

        @Comment("Maximum time to wait for server to report alive before failing (in seconds)")
        private int maxStartupWaitSeconds = 120;

        public ServerSettings() {
        }

        public ServerSettings(int idleTimeoutMinutes, int startupGracePeriodSeconds, int pollIntervalSeconds, int maxStartupWaitSeconds) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            this.startupGracePeriodSeconds = startupGracePeriodSeconds;
            this.pollIntervalSeconds = pollIntervalSeconds;
            this.maxStartupWaitSeconds = maxStartupWaitSeconds;
        }

        public int idleTimeoutMinutes() {
            return idleTimeoutMinutes;
        }

        public int startupGracePeriodSeconds() {
            return startupGracePeriodSeconds;
        }

        public int pollIntervalSeconds() {
            return pollIntervalSeconds;
        }

        public int maxStartupWaitSeconds() {
            return maxStartupWaitSeconds;
        }
    }
}
