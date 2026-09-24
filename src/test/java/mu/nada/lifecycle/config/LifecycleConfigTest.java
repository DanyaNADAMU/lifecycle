package mu.nada.lifecycle.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleConfigTest {

    @Test
    void testUnitResolutionDefaults() {
        LifecycleConfig.BridgeSettings bridge = new LifecycleConfig.BridgeSettings();
        assertEquals("start", bridge.hooks().start());
        assertEquals("stop", bridge.hooks().stop());
        assertEquals("status", bridge.hooks().status());
        assertEquals("server", bridge.parameterName());
        assertEquals("mc@{server}", bridge.unitTemplate());

        assertEquals("mc@pvp", bridge.resolveUnitName("pvp"));
        assertEquals("mc@pillar", bridge.resolveUnitName("pillar"));
    }

    @Test
    void testCustomUnitResolution() {
        LifecycleConfig.BridgeSettings bridge = new LifecycleConfig.BridgeSettings(
                "http://localhost:8012",
                5,
                "super-secret-token",
                "target",
                "custom-{server}.service",
                new LifecycleConfig.HookSettings("wake", "sleep", "check")
        );

        assertEquals("super-secret-token", bridge.token());
        assertEquals("wake", bridge.hooks().start());
        assertEquals("sleep", bridge.hooks().stop());
        assertEquals("check", bridge.hooks().status());
        assertEquals("target", bridge.parameterName());
        assertEquals("custom-lobby.service", bridge.resolveUnitName("lobby"));
    }

    @Test
    void testConfigDeserializationFromYaml() throws Exception {
        String yaml = """
                bridge:
                  url: "http://host.containers.internal:8012"
                  timeout-seconds: 10
                  token: "my-secret-123"
                  parameter-name: "server"
                  unit-template: "mc@{server}"
                  hooks:
                    start: "start"
                    stop: "stop"
                    status: "status"
                limbo-server: "limbo"
                default-language: "en"
                auto: false
                defaults:
                  idle-timeout-minutes: 8
                  startup-grace-period-seconds: 150
                  poll-interval-seconds: 2
                  max-startup-wait-seconds: 100
                servers:
                  pvp:
                    unit: "mc@pvp-custom"
                    idle-timeout-minutes: 15
                  lobby:
                    enabled: false
                  forge:
                    max-startup-wait-seconds: 300
                """;

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new StringReader(yaml)))
                .build();

        var node = loader.load();
        LifecycleConfig config = node.get(LifecycleConfig.class);

        assertNotNull(config);
        assertEquals("http://host.containers.internal:8012", config.bridge().url());
        assertEquals(10, config.bridge().timeoutSeconds());
        assertEquals("my-secret-123", config.bridge().token());
        assertEquals("en", config.defaultLanguage());
        assertFalse(config.auto());

        // Test defaults
        assertEquals(8, config.defaults().idleTimeoutMinutes());
        assertEquals(150, config.defaults().startupGracePeriodSeconds());
        assertEquals(2, config.defaults().pollIntervalSeconds());
        assertEquals(100, config.defaults().maxStartupWaitSeconds());

        // Test pvp override merged with defaults
        assertTrue(config.servers().containsKey("pvp"));
        var pvpOverride = config.servers().get("pvp");
        var pvpEffective = config.defaults().mergeWith(pvpOverride);
        assertEquals("mc@pvp-custom", pvpEffective.unit());
        assertEquals(15, pvpEffective.idleTimeoutMinutes());
        assertEquals(150, pvpEffective.startupGracePeriodSeconds()); // inherited from defaults
        assertEquals(2, pvpEffective.pollIntervalSeconds());         // inherited from defaults
        assertEquals(100, pvpEffective.maxStartupWaitSeconds());     // inherited from defaults
        assertTrue(pvpEffective.isEnabled());

        // Test lobby disabled
        assertTrue(config.servers().containsKey("lobby"));
        var lobbyOverride = config.servers().get("lobby");
        var lobbyEffective = config.defaults().mergeWith(lobbyOverride);
        assertFalse(lobbyEffective.isEnabled());

        // Test forge timeout override merged with defaults
        assertTrue(config.servers().containsKey("forge"));
        var forgeOverride = config.servers().get("forge");
        var forgeEffective = config.defaults().mergeWith(forgeOverride);
        assertEquals(8, forgeEffective.idleTimeoutMinutes());        // inherited from defaults
        assertEquals(300, forgeEffective.maxStartupWaitSeconds());   // overridden
    }
}
