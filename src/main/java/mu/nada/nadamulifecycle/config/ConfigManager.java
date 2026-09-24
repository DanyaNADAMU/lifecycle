package mu.nada.nadamulifecycle.config;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private final Path dataDirectory;
    private final Path configPath;
    private final Logger logger;
    private LifecycleConfig config;

    public ConfigManager(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.configPath = dataDirectory.resolve("config.yml");
        this.logger = logger;
    }

    public void reload() throws ConfigurateException {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to initialize configuration directory or default file: {}", configPath, e);
        }

        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .nodeStyle(NodeStyle.BLOCK)
                .build();

        CommentedConfigurationNode root = loader.load();
        LifecycleConfig loaded = root.get(LifecycleConfig.class);

        if (loaded == null) {
            loaded = new LifecycleConfig();
        }

        root.set(LifecycleConfig.class, loaded);
        loader.save(root);

        this.config = loaded;
    }

    public LifecycleConfig config() {
        return config;
    }
}
