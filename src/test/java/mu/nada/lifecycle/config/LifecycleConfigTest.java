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
                "http://localhost:9000",
                5,
                "target",
                "custom-{server}.service",
                new LifecycleConfig.HookSettings("wake", "sleep", "check")
        );

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
                  url: "http://host.containers.internal:9000"
                  timeout-seconds: 10
                  parameter-name: "server"
                  unit-template: "mc@{server}"
                  hooks:
                    start: "start"
                    stop: "stop"
                    status: "status"
                limbo-server: "limbo"
                servers:
                  pvp:
                    unit: "mc@pvp-custom"
                    idle-timeout-minutes: 15
                    startup-grace-period-seconds: 200
                    poll-interval-seconds: 1
                    max-startup-wait-seconds: 60
                """;

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new java.io.BufferedReader(new StringReader(yaml)))
                .build();

        var node = loader.load();
        LifecycleConfig config = node.get(LifecycleConfig.class);

        assertNotNull(config);
        assertEquals("http://host.containers.internal:9000", config.bridge().url());
        assertEquals(10, config.bridge().timeoutSeconds());
        assertEquals("start", config.bridge().hooks().start());
        assertEquals("stop", config.bridge().hooks().stop());
        assertEquals("status", config.bridge().hooks().status());

        assertTrue(config.servers().containsKey("pvp"));
        var pvp = config.servers().get("pvp");
        assertEquals("mc@pvp-custom", pvp.unit());
        assertEquals(15, pvp.idleTimeoutMinutes());
        assertEquals(200, pvp.startupGracePeriodSeconds());
        assertEquals(1, pvp.pollIntervalSeconds());
        assertEquals(60, pvp.maxStartupWaitSeconds());
    }
}
