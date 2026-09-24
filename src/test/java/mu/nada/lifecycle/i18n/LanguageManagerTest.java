package mu.nada.lifecycle.i18n;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import mu.nada.lifecycle.config.MessagesConfig;
import mu.nada.lifecycle.util.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LanguageManagerTest {

    @TempDir
    Path tempDir;

    private LanguageManager languageManager;
    private MessageService messageService;

    @BeforeEach
    void setUp() throws Exception {
        languageManager = new LanguageManager(tempDir, LoggerFactory.getLogger("TestLogger"));
        languageManager.reload("ru");
        messageService = new MessageService(languageManager);
    }

    @Test
    void testEnsureDefaultsCreated() throws Exception {
        Path ruFile = tempDir.resolve("languages/ru.yml");
        Path enFile = tempDir.resolve("languages/en.yml");

        assertTrue(Files.exists(ruFile), "ru.yml must be automatically generated");
        assertTrue(Files.exists(enFile), "en.yml must be automatically generated");

        Map<String, MessagesConfig> loaded = languageManager.getLoadedLanguages();
        assertTrue(loaded.containsKey("ru"), "Loaded languages must contain 'ru'");
        assertTrue(loaded.containsKey("en"), "Loaded languages must contain 'en'");

        MessagesConfig ruConfig = loaded.get("ru");
        MessagesConfig enConfig = loaded.get("en");

        assertTrue(ruConfig.serverStarting().contains("запускается"));
        assertTrue(enConfig.serverStarting().contains("is starting"));

        String ruYaml = Files.readString(ruFile);
        assertTrue(ruYaml.contains("server-starting:"), "ru.yml must contain kebab-case keys");
        assertTrue(ruYaml.contains("status-header:"), "ru.yml must contain status header");
    }

    @Test
    void testPlayerLocaleResolution() {
        Player ruPlayer = createFakePlayer(Locale.forLanguageTag("ru-RU"));
        Player enPlayer = createFakePlayer(Locale.forLanguageTag("en-US"));
        Player unknownPlayer = createFakePlayer(Locale.forLanguageTag("es-ES"));

        assertEquals(languageManager.getLoadedLanguages().get("ru").serverReady(),
                languageManager.getMessages(ruPlayer).serverReady());

        assertEquals(languageManager.getLoadedLanguages().get("en").serverReady(),
                languageManager.getMessages(enPlayer).serverReady());

        // Unknown locale falls back to defaultLanguage ('ru')
        assertEquals(languageManager.getLoadedLanguages().get("ru").serverReady(),
                languageManager.getMessages(unknownPlayer).serverReady());
    }

    @Test
    void testDialectSupport() throws Exception {
        Path ruUaFile = tempDir.resolve("languages/ru-ua.yml");
        String ruUaContent = "server-ready: \"<green>Сервер {server} готовий! Підключення...</green>\"\n";
        Files.writeString(ruUaFile, ruUaContent);

        languageManager.reload("ru");

        Player ruUaPlayer = createFakePlayer(Locale.forLanguageTag("ru-UA"));
        Player ruKzPlayer = createFakePlayer(Locale.forLanguageTag("ru-KZ")); // no dialect file, falls back to ru
        Player enGbPlayer = createFakePlayer(Locale.forLanguageTag("en-GB")); // no dialect file, falls back to en

        assertEquals("<green>Сервер {server} готовий! Підключення...</green>",
                languageManager.getMessages(ruUaPlayer).serverReady());

        // ru-KZ falls back to base 'ru'
        assertTrue(languageManager.getMessages(ruKzPlayer).serverReady().contains("готов"));

        // en-GB falls back to base 'en'
        assertTrue(languageManager.getMessages(enGbPlayer).serverReady().contains("is ready"));
    }

    @Test
    void testMessageServiceComponentAndPlaceholders() {
        Player ruPlayer = createFakePlayer(Locale.forLanguageTag("ru"));
        Player enPlayer = createFakePlayer(Locale.forLanguageTag("en"));

        Component ruComp = messageService.getComponent(ruPlayer, MessagesConfig::serverReady, Map.of("server", "pvp"));
        Component enComp = messageService.getComponent(enPlayer, MessagesConfig::serverReady, Map.of("server", "pvp"));

        String ruPlain = PlainTextComponentSerializer.plainText().serialize(ruComp);
        String enPlain = PlainTextComponentSerializer.plainText().serialize(enComp);

        assertTrue(ruPlain.contains("Сервер pvp готов!"), "RU component should contain Russian placeholder value");
        assertTrue(enPlain.contains("Server pvp is ready!"), "EN component should contain English placeholder value");
    }

    private Player createFakePlayer(Locale locale) {
        PlayerSettings settings = (PlayerSettings) Proxy.newProxyInstance(
                PlayerSettings.class.getClassLoader(),
                new Class<?>[]{PlayerSettings.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getLocale")) {
                        return locale;
                    }
                    return null;
                }
        );

        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getPlayerSettings")) {
                        return settings;
                    }
                    return null;
                }
        );
    }
}
