// Copyright JasonHorkles and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package net.silverstonemc.expensivedeaths;

import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class UpdateChecker implements Listener {
    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private final JavaPlugin plugin;
    private static final String PLUGIN_ID = "2bq9PFVl";
    private static final String PLUGIN_URL = "https://modrinth.com/plugin/" + PLUGIN_ID + "/changelog";

    @EventHandler(ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        String pluginName = plugin.getDescription().getName();

        if (event.getPlayer().hasPermission(pluginName.toLowerCase() + ".updatenotifs"))
            // Check for updates asynchronously
            new BukkitRunnable() {
                @Override
                public void run() {
                    String current = plugin.getDescription().getVersion();
                    String latest = getLatestVersion();

                    if (latest == null) return;
                    if (!current.equals(latest)) event.getPlayer()
                        .sendMessage(ChatColor.YELLOW + "An update is available for " + pluginName + "! " + ChatColor.GOLD + "(" + current + " → " + latest + ")\n" + ChatColor.DARK_AQUA + PLUGIN_URL);
                }
            }.runTaskAsynchronously(plugin);
    }

    public void logUpdate(String current, String latest) {
        String pluginName = plugin.getDescription().getName();

        plugin.getLogger()
            .warning("An update is available for " + pluginName + "! (" + current + " → " + latest + ")");
        plugin.getLogger().warning(PLUGIN_URL);
    }

    @Nullable
    public String getLatestVersion() {
        try {
            // Send the request
            InputStream url = new URI("https://api.modrinth.com/v2/project/" + PLUGIN_ID + "/version").toURL()
                .openStream();

            // Read the response
            JSONObject response = new JSONArray(new String(
                url.readAllBytes(),
                StandardCharsets.UTF_8)).getJSONObject(0);
            url.close();

            return response.getString("version_number");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
