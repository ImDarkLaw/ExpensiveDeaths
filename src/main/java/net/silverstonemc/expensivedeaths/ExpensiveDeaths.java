/*
 * ExpensiveDeaths - a Minecraft plugin
 * Copyright (C) 2026 JasonHorkles and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.silverstonemc.expensivedeaths;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class ExpensiveDeaths extends JavaPlugin implements Listener, CommandExecutor {
    private Economy econ;
    private Permission perms;
    private final Map<Execution.Type, Execution> executions = new HashMap<>();
    private static ExpensiveDeaths instance;

    @Override
    public void onEnable() {
        instance = this;

        //noinspection ResultOfObjectAllocationIgnored
        new Metrics(this, 30399);

        setupEconomy();
        setupPermissions();
        saveDefaultConfig();
        loadExecutions();

        getServer().getPluginManager().registerEvents(new DeathEvent(this), this);
        getServer().getPluginManager().registerEvents(new RespawnEvent(this), this);
        getServer().getPluginManager().registerEvents(new UpdateChecker(this), this);

        // Log version update
        new BukkitRunnable() {
            @Override
            public void run() {
                String latest = new UpdateChecker(instance).getLatestVersion();
                String current = instance.getDescription().getVersion();

                if (latest == null) return;
                if (!current.equals(latest)) new UpdateChecker(instance).logUpdate(current, latest);
            }
        }.runTaskLaterAsynchronously(this, 5L);
    }

    public boolean onCommand(CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        saveDefaultConfig();
        reloadConfig();
        loadExecutions();

        sender.sendMessage(ChatColor.GREEN + "ExpensiveDeaths reloaded!");
        return true;
    }

    public static ExpensiveDeaths getInstance() {
        return instance;
    }

    public Economy getEconomy() {
        return econ;
    }

    public Permission getPermissions() {
        return perms;
    }

    public void run(Execution.Type type, Player player, Player agent, Function<String, String> parser) {
        Execution execution = executions.get(type);
        if (execution != null) execution.run(player, agent, parser, type.isConsole());
    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager()
            .getRegistration(Economy.class);
        if (rsp == null) return;
        econ = rsp.getProvider();
    }

    private void setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(
            Permission.class);
        if (rsp == null) return;
        perms = rsp.getProvider();
    }

    private void loadExecutions() {
        executions.clear();
        loadExecution(Execution.Type.DEATH_CONSOLE, "console-commands-on-death");
        loadExecution(Execution.Type.DEATH_PLAYER, "player-commands-on-death");
        loadExecution(Execution.Type.KILL_CONSOLE, "console-commands-on-killed");
        loadExecution(Execution.Type.KILL_PLAYER, "player-commands-on-killed");
        loadExecution(Execution.Type.RESPAWN_CONSOLE, "console-commands-on-respawn");
        loadExecution(Execution.Type.RESPAWN_PLAYER, "player-commands-on-respawn");
    }

    private void loadExecution(Execution.Type type, String key) {
        Execution execution = Execution.of(getConfig().get("bonus." + key));
        if (execution != null) executions.put(type, execution);
    }
}
