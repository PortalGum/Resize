package com.resize;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class UpdateChecker implements Listener {

    private final Resize plugin;
    private final String projectSlug;

    private String latestVersion;
    private boolean updateAvailable = false;

    private long lastConsoleReminder = 0;

    // 30 minutes
    private static final long CHECK_INTERVAL = 20L * 60 * 30;

    // 2 hours
    private static final long CONSOLE_REMINDER_INTERVAL = 1000L * 60 * 60 * 2;

    public UpdateChecker(Resize plugin, String projectSlug) {
        this.plugin = plugin;
        this.projectSlug = projectSlug;

        startUpdateTask();
    }

    private void startUpdateTask() {

        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) return;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {

            boolean hadUpdateBefore = updateAvailable;

            checkUpdate(hadUpdateBefore);

            if (updateAvailable) {
                long now = System.currentTimeMillis();

                if (now - lastConsoleReminder >= CONSOLE_REMINDER_INTERVAL) {
                    lastConsoleReminder = now;

                    plugin.getServer().getConsoleSender().sendMessage(
                            plugin.msg("update-available")
                                    .replace("{current}", plugin.getDescription().getVersion())
                                    .replace("{latest}", latestVersion)
                    );
                }
            }

        }, 0L, CHECK_INTERVAL);
    }

    private void checkUpdate(boolean hadUpdateBefore) {

        try {

            updateAvailable = false;
            latestVersion = null;

            String apiUrl = "https://api.modrinth.com/v2/project/" + projectSlug + "/version";

            HttpURLConnection connection =
                    (HttpURLConnection) new URL(apiUrl).openConnection();

            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Resize-Update-Checker");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream())
            );

            String response = reader.lines().collect(Collectors.joining());
            reader.close();

            JsonArray versions = JsonParser.parseString(response).getAsJsonArray();

            String currentVersion = plugin.getDescription().getVersion();

            String newestRelease = null;

            for (int i = 0; i < versions.size(); i++) {

                JsonObject versionObject = versions.get(i).getAsJsonObject();

                String versionType = versionObject
                        .get("version_type")
                        .getAsString();

                if (!versionType.equalsIgnoreCase("release")) continue;

                String versionNumber = versionObject
                        .get("version_number")
                        .getAsString();

                if (newestRelease == null || isNewerVersion(newestRelease, versionNumber)) {
                    newestRelease = versionNumber;
                }
            }

            // check
            if (newestRelease != null && isNewerVersion(currentVersion, newestRelease)) {

                updateAvailable = true;
                latestVersion = newestRelease;

                if (!hadUpdateBefore) {
                    plugin.getServer().getConsoleSender().sendMessage(
                            plugin.msg("update-available")
                                    .replace("{current}", currentVersion)
                                    .replace("{latest}", latestVersion)
                    );

                    lastConsoleReminder = System.currentTimeMillis();
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Update check failed.");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        if (!plugin.getConfig().getBoolean("update-checker.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("update-checker.notify-ops", true)) return;
        if (!updateAvailable) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;

        String raw = plugin.msg("update-available")
                .replace("{current}", plugin.getDescription().getVersion());

        String[] parts = raw.split("\\{latest\\}");

        String before = plugin.color(parts[0]);
        String after = parts.length > 1 ? plugin.color(parts[1]) : "";

        String url = "https://modrinth.com/plugin/" +
                projectSlug + "/version/" + latestVersion;

        TextComponent message = new TextComponent();

        message.addExtra(new TextComponent(before));

        TextComponent clickable = new TextComponent(plugin.color("&a" + latestVersion));
        clickable.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        clickable.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(plugin.color("&7Click to open download page")).create()
        ));

        message.addExtra(clickable);
        message.addExtra(new TextComponent(after));

        player.spigot().sendMessage(message);
    }

    private boolean isNewerVersion(String currentVersion, String latestVersion) {

        String[] current = currentVersion.split("\\.");
        String[] latest = latestVersion.split("\\.");

        for (int i = 0; i < Math.max(current.length, latest.length); i++) {

            int c = i < current.length ? parseIntSafe(current[i]) : 0;
            int l = i < latest.length ? parseIntSafe(latest[i]) : 0;

            if (l > c) return true;
            if (l < c) return false;
        }

        return false;
    }

    private int parseIntSafe(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}