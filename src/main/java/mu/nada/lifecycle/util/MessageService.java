package mu.nada.lifecycle.util;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import mu.nada.lifecycle.config.MessagesConfig;
import mu.nada.lifecycle.i18n.LanguageManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.function.Function;

public class MessageService {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LanguageManager languageManager;

    public MessageService(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    public Component parse(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return Component.empty();
        }
        return miniMessage.deserialize(rawText);
    }

    public Component parse(String rawText, Map<String, String> placeholders) {
        if (rawText == null || rawText.isEmpty()) {
            return Component.empty();
        }
        String result = rawText;
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return miniMessage.deserialize(result);
    }

    public Component getComponent(CommandSource source, Function<MessagesConfig, String> messageSelector) {
        return getComponent(source, messageSelector, Map.of());
    }

    public Component getComponent(CommandSource source, Function<MessagesConfig, String> messageSelector, Map<String, String> placeholders) {
        MessagesConfig config = languageManager.getMessages(source);
        String template = messageSelector.apply(config);
        return parse(config.prefix() + template, placeholders);
    }

    public Component getRawComponent(CommandSource source, Function<MessagesConfig, String> messageSelector) {
        return getRawComponent(source, messageSelector, Map.of());
    }

    public Component getRawComponent(CommandSource source, Function<MessagesConfig, String> messageSelector, Map<String, String> placeholders) {
        MessagesConfig config = languageManager.getMessages(source);
        String template = messageSelector.apply(config);
        return parse(template, placeholders);
    }

    public void sendMessage(CommandSource source, Function<MessagesConfig, String> messageSelector) {
        source.sendMessage(getComponent(source, messageSelector));
    }

    public void sendMessage(CommandSource source, Function<MessagesConfig, String> messageSelector, Map<String, String> placeholders) {
        source.sendMessage(getComponent(source, messageSelector, placeholders));
    }

    public void sendRawMessage(Audience audience, Component component) {
        audience.sendMessage(component);
    }

    public void sendRawMessage(Audience audience, String rawText) {
        audience.sendMessage(parse(rawText));
    }

    public MessagesConfig getMessages(CommandSource source) {
        return languageManager.getMessages(source);
    }

    public MessagesConfig getMessages(Player player) {
        return languageManager.getMessagesForPlayer(player);
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public MessagesConfig getDefaultConfig() {
        return languageManager.getDefaultMessages();
    }
}
