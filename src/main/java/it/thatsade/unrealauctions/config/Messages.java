package it.thatsade.unrealauctions.config;

import it.thatsade.unrealauctions.UnrealAuctionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Messages {

    private final UnrealAuctionsPlugin plugin;
    private YamlConfiguration config;
    private String prefix;

    public Messages(UnrealAuctionsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        // Merge defaults from jar
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
        }

        prefix = translate(config.getString("prefix", "&8[&6AH&8] &r"));
    }

    public String getRaw(String key) {
        return config.getString(key, "&c[Missing message: " + key + "]");
    }

    private String translate(String raw) {
        //noinspection deprecation
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', raw);
    }

    public String get(String key) {
        return prefix + translate(getRaw(key));
    }

    public String get(String key, Map<String, String> placeholders) {
        String msg = getRaw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return prefix + translate(msg);
    }

    /** Colourises an ad-hoc string built at the call site rather than a message key. */
    public String color(String raw) {
        return translate(raw);
    }

    public String getNoPrefix(String key) {
        return translate(getRaw(key));
    }

    public String getNoPrefix(String key, Map<String, String> placeholders) {
        String msg = getRaw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue());
        }
        return translate(msg);
    }

    public Component getComponent(String key) {
        return LegacyComponentSerializer.legacySection().deserialize(get(key));
    }

    public Component getComponent(String key, Map<String, String> placeholders) {
        return LegacyComponentSerializer.legacySection().deserialize(get(key, placeholders));
    }

    public void send(Player player, String key) {
        player.sendMessage(getComponent(key));
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        player.sendMessage(getComponent(key, placeholders));
    }
}
