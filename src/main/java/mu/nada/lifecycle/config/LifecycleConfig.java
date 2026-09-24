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

    @Comment("Default language for messages if client locale cannot be determined (e.g. 'ru', 'en')")
    private String defaultLanguage = "ru";

    @Comment("Automatically manage all servers registered in velocity.toml (except limbo and disabled)")
    private boolean auto = true;

    @Comment("Default settings applied to any managed server unless overridden")
    private ServerSettings defaults = new ServerSettings(10, 180, 2, 120);

    @Comment("Server-specific overrides or whitelist")
    private Map<String, ServerSettings> servers = new HashMap<>(Map.of(
            "forge", new ServerSettings(15, 300, 3, 300)
    ));

    public BridgeSettings bridge() {
        return bridge;
    }

    public String limboServer() {
        return limboServer;
    }

    public String defaultLanguage() {
        return defaultLanguage != null && !defaultLanguage.isBlank() ? defaultLanguage : "ru";
    }

    public boolean auto() {
        return auto;
    }

    public ServerSettings defaults() {
        return defaults != null ? defaults : new ServerSettings(10, 180, 2, 120);
    }

    public Map<String, ServerSettings> servers() {
        return servers != null ? servers : Map.of();
    }

    @ConfigSerializable
    public static class BridgeSettings {
        @Comment("URL of the host webhook bridge")
        private String url = "http://host.containers.internal:8012";

        @Comment("Timeout in seconds for webhook HTTP requests")
        private int timeoutSeconds = 5;

        @Comment("Optional authorization token sent in the X-Bridge-Token HTTP header")
        private String token = "";

        @Comment("Name of the query parameter passed to the webhook (defaults to 'server')")
        private String parameterName = "server";

        @Comment("Template for the systemd unit name. '{server}' is replaced with the server name")
        private String unitTemplate = "mc@{server}";

        @Comment("Configurable hook identifiers matching hooks.json in adnanh/webhook")
        private HookSettings hooks = new HookSettings();

        public BridgeSettings() {
        }

        public BridgeSettings(String url, int timeoutSeconds, String parameterName, String unitTemplate, HookSettings hooks) {
            this(url, timeoutSeconds, "", parameterName, unitTemplate, hooks);
        }

        public BridgeSettings(String url, int timeoutSeconds, String token, String parameterName, String unitTemplate, HookSettings hooks) {
            this.url = url;
            this.timeoutSeconds = timeoutSeconds;
            this.token = token;
            this.parameterName = parameterName;
            this.unitTemplate = unitTemplate;
            this.hooks = hooks;
        }

        public String url() {
            return url;
        }

        public int timeoutSeconds() {
            return timeoutSeconds;
        }

        public String token() {
            return token != null ? token : "";
        }

        public String parameterName() {
            return parameterName != null && !parameterName.isBlank() ? parameterName : "server";
        }

        public String unitTemplate() {
            return unitTemplate != null ? unitTemplate : "mc@{server}";
        }

        public HookSettings hooks() {
            return hooks != null ? hooks : new HookSettings();
        }

        public String resolveUnitName(String serverName) {
            String tmpl = unitTemplate();
            if (!tmpl.isBlank()) {
                return tmpl.replace("{server}", serverName);
            }
            return serverName;
        }
    }

    @ConfigSerializable
    public static class HookSettings {
        @Comment("Hook ID for starting a server")
        private String start = "start";

        @Comment("Hook ID for stopping a server")
        private String stop = "stop";

        @Comment("Hook ID for checking server status")
        private String status = "status";

        public HookSettings() {
        }

        public HookSettings(String start, String stop, String status) {
            this.start = start;
            this.stop = stop;
            this.status = status;
        }

        public String start() {
            return start != null && !start.isBlank() ? start : "start";
        }

        public String stop() {
            return stop != null && !stop.isBlank() ? stop : "stop";
        }

        public String status() {
            return status != null && !status.isBlank() ? status : "status";
        }
    }

    @ConfigSerializable
    public static class ServerSettings {
        @Comment("Optional custom systemd unit name. If omitted, bridge.unit-template is used")
        private String unit = null;

        @Comment("Whether this server is managed by lifecycle (set to false to exclude in auto mode)")
        private Boolean enabled = null;

        @Comment("How long the server can stay completely empty before being stopped (in minutes)")
        private Integer idleTimeoutMinutes = null;

        @Comment("Minimum time after boot before idle shutdown checks are enforced (in seconds)")
        private Integer startupGracePeriodSeconds = null;

        @Comment("Interval between TCP pings while checking if server is alive (in seconds)")
        private Integer pollIntervalSeconds = null;

        @Comment("Maximum time to wait for server to report alive before failing (in seconds)")
        private Integer maxStartupWaitSeconds = null;

        public ServerSettings() {
        }

        public ServerSettings(int idleTimeoutMinutes, int startupGracePeriodSeconds, int pollIntervalSeconds, int maxStartupWaitSeconds) {
            this(null, true, idleTimeoutMinutes, startupGracePeriodSeconds, pollIntervalSeconds, maxStartupWaitSeconds);
        }

        public ServerSettings(String unit, Boolean enabled, Integer idleTimeoutMinutes, Integer startupGracePeriodSeconds, Integer pollIntervalSeconds, Integer maxStartupWaitSeconds) {
            this.unit = unit;
            this.enabled = enabled;
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            this.startupGracePeriodSeconds = startupGracePeriodSeconds;
            this.pollIntervalSeconds = pollIntervalSeconds;
            this.maxStartupWaitSeconds = maxStartupWaitSeconds;
        }

        public String unit() {
            return unit != null ? unit : "";
        }

        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public Boolean rawEnabled() {
            return enabled;
        }

        public int idleTimeoutMinutes() {
            return idleTimeoutMinutes != null ? idleTimeoutMinutes : 10;
        }

        public int startupGracePeriodSeconds() {
            return startupGracePeriodSeconds != null ? startupGracePeriodSeconds : 180;
        }

        public int pollIntervalSeconds() {
            return pollIntervalSeconds != null ? pollIntervalSeconds : 2;
        }

        public int maxStartupWaitSeconds() {
            return maxStartupWaitSeconds != null ? maxStartupWaitSeconds : 120;
        }

        public ServerSettings mergeWith(ServerSettings override) {
            if (override == null) {
                return this;
            }

            String effectiveUnit = (override.unit != null && !override.unit.isBlank())
                    ? override.unit
                    : this.unit();

            boolean effectiveEnabled = override.enabled != null
                    ? override.enabled
                    : this.isEnabled();

            int effectiveIdle = override.idleTimeoutMinutes != null
                    ? override.idleTimeoutMinutes
                    : this.idleTimeoutMinutes();

            int effectiveGrace = override.startupGracePeriodSeconds != null
                    ? override.startupGracePeriodSeconds
                    : this.startupGracePeriodSeconds();

            int effectivePoll = override.pollIntervalSeconds != null
                    ? override.pollIntervalSeconds
                    : this.pollIntervalSeconds();

            int effectiveMaxStartup = override.maxStartupWaitSeconds != null
                    ? override.maxStartupWaitSeconds
                    : this.maxStartupWaitSeconds();

            return new ServerSettings(
                    effectiveUnit,
                    effectiveEnabled,
                    effectiveIdle,
                    effectiveGrace,
                    effectivePoll,
                    effectiveMaxStartup
            );
        }
    }
}
