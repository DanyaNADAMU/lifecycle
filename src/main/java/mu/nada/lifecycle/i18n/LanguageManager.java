package mu.nada.lifecycle.i18n;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import mu.nada.lifecycle.config.MessagesConfig;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LanguageManager {

    private final Path languagesDirectory;
    private final Logger logger;
    private final Map<String, MessagesConfig> languages = new ConcurrentHashMap<>();
    private String defaultLanguage = "ru";

    public LanguageManager(Path dataDirectory, Logger logger) {
        this.languagesDirectory = dataDirectory.resolve("languages");
        this.logger = logger;
    }

    public void reload(String defaultLanguage) throws ConfigurateException {
        this.defaultLanguage = (defaultLanguage != null && !defaultLanguage.isBlank())
                ? defaultLanguage.toLowerCase().replace('_', '-')
                : "ru";

        try {
            if (!Files.exists(languagesDirectory)) {
                Files.createDirectories(languagesDirectory);
            }
        } catch (IOException e) {
            logger.error("Failed to create languages directory: {}", languagesDirectory, e);
        }

        // Generate default language dictionaries if missing
        ensureLanguageFile("ru.yml", new MessagesConfig());
        ensureLanguageFile("en.yml", MessagesConfig.createEnglishDefault());

        // Load all .yml / .yaml files from languages/
        languages.clear();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(languagesDirectory, "*.{yml,yaml}")) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String rawName = fileName.substring(0, fileName.lastIndexOf('.')).toLowerCase();

                MessagesConfig config = loadLanguageFile(path);
                registerLanguage(rawName, config);
            }
        } catch (IOException e) {
            logger.error("Error reading language files from {}", languagesDirectory, e);
        }

        logger.info("Loaded {} language dictionary mapping(s) (default: '{}')",
                languages.size(), this.defaultLanguage);
    }

    private void registerLanguage(String name, MessagesConfig config) {
        String hyphenated = name.replace('_', '-');
        String underscored = name.replace('-', '_');

        languages.put(hyphenated, config);
        languages.put(underscored, config);
    }

    private void ensureLanguageFile(String fileName, MessagesConfig defaultConfig) throws ConfigurateException {
        Path filePath = languagesDirectory.resolve(fileName);
        if (!Files.exists(filePath)) {
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(filePath)
                    .nodeStyle(NodeStyle.BLOCK)
                    .build();

            CommentedConfigurationNode node = loader.load();
            node.set(MessagesConfig.class, defaultConfig);
            loader.save(node);
        }
    }

    private MessagesConfig loadLanguageFile(Path path) throws ConfigurateException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(path)
                .nodeStyle(NodeStyle.BLOCK)
                .build();

        CommentedConfigurationNode root = loader.load();
        MessagesConfig config = root.get(MessagesConfig.class);
        if (config == null) {
            config = new MessagesConfig();
        }
        return config;
    }

    public MessagesConfig getMessages(CommandSource source) {
        if (source instanceof Player player) {
            return getMessagesForPlayer(player);
        }
        return getDefaultMessages();
    }

    public MessagesConfig getMessagesForPlayer(Player player) {
        try {
            if (player.getPlayerSettings() != null) {
                Locale locale = player.getPlayerSettings().getLocale();
                if (locale != null) {
                    // 1. Check exact dialect first (e.g. ru-ua, en-us)
                    String tagCode = locale.toLanguageTag().toLowerCase();
                    if (languages.containsKey(tagCode)) {
                        return languages.get(tagCode);
                    }

                    String stringCode = locale.toString().toLowerCase().replace('_', '-');
                    if (languages.containsKey(stringCode)) {
                        return languages.get(stringCode);
                    }

                    // 2. Check base language (e.g. ru, en)
                    String baseLang = locale.getLanguage().toLowerCase();
                    if (languages.containsKey(baseLang)) {
                        return languages.get(baseLang);
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback to default if player settings is inaccessible
        }

        return getDefaultMessages();
    }

    public MessagesConfig getDefaultMessages() {
        MessagesConfig config = languages.get(defaultLanguage);
        if (config != null) {
            return config;
        }

        String normalizedDefault = defaultLanguage.replace('_', '-');
        if (languages.containsKey(normalizedDefault)) {
            return languages.get(normalizedDefault);
        }

        return languages.getOrDefault("ru",
                languages.values().stream().findFirst().orElseGet(MessagesConfig::new));
    }

    public Map<String, MessagesConfig> getLoadedLanguages() {
        return languages;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }
}
