// Copyright JasonHorkles and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package net.silverstonemc.expensivedeaths;

import com.google.common.base.Suppliers;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import me.clip.placeholderapi.PlaceholderAPI;

public abstract class Execution {
    // Cache PlaceholderAPI availability to avoid checking plugin manager on every command run
    private static final Supplier<Boolean> USE_PLACEHOLDERAPI = Suppliers.memoize(() -> Bukkit
        .getPluginManager().isPluginEnabled("PlaceholderAPI"));

    // Flexible key matching allows config aliases like probability/prob, stop/break, etc
    private static final Pattern KEY_CHANCE = Pattern.compile("(?i)(test-?)?(chance|prob(ability)?)");
    private static final Pattern KEY_CANCEL = Pattern.compile("(?i)break|stop|cancel(ling)?");
    private static final Pattern KEY_PERMISSION = Pattern.compile("(?i)(meet-?)?perm(ission)?");
    private static final Pattern KEY_EXECUTION = Pattern.compile(
        "(?i)run|(run-?)?(cmds?|commands?)|execut(e|ions?)");

    @Nullable
    public static Execution of(Object object) {
        // A plain string is treated as a single command execution
        if (object instanceof String) return new SimpleExecution((String) object);

        else if (object instanceof Iterable) {
            // Lists can contain nested structures, so parse each entry recursively
            List<Execution> executions = new ArrayList<>();
            for (Object o : (Iterable<?>) object) {
                Execution execution = of(o);
                if (execution != null) executions.add(execution);
            }

            // Default advanced execution for list input (no chance/permission/cancel metadata)
            if (!executions.isEmpty()) return new AdvancedExecution(0.0, false, null, executions);

        } else if (object instanceof Map) {
            double chance = 0.0;
            boolean cancelling = false;
            String permission = null;
            List<Execution> executions = new ArrayList<>();

            // Parse advanced execution options from object keys
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object value = entry.getValue();

                if (KEY_CHANCE.matcher(key).matches()) try {
                    chance = Double.parseDouble(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                }

                else if (KEY_CANCEL.matcher(key).matches())
                    cancelling = String.valueOf(value).equalsIgnoreCase("true");

                else if (KEY_PERMISSION.matcher(key).matches()) permission = String.valueOf(value);

                else if (KEY_EXECUTION.matcher(key).matches()) {
                    Execution execution = of(value);
                    if (execution != null) executions.add(execution);
                }
            }

            if (!executions.isEmpty()) return new AdvancedExecution(
                chance,
                cancelling,
                permission,
                executions);
        }
        return null;
    }

    public void run(Player player, Player agent, Function<String, String> parser, boolean console) {
        run(console ? Bukkit.getConsoleSender() : player, player, agent, parser);
    }

    public abstract boolean run(CommandSender sender, Player player, Player agent, Function<String, String> parser);

    public void run(CommandSender sender, Player player, Player agent, String cmd, Function<String, String> parser) {
        String s = parser.apply(cmd);

        // Optional PlaceholderAPI support for users who enable it in config
        if (USE_PLACEHOLDERAPI.get() && ExpensiveDeaths.getInstance().getConfig().getBoolean(
            "bonus.parse-placeholders")) {
            s = PlaceholderAPI.setPlaceholders(player, s);
            if (agent != null) s = PlaceholderAPI.setBracketPlaceholders(agent, s);
        }

        Bukkit.dispatchCommand(sender, s);
    }

    public static class SimpleExecution extends Execution {
        private final String cmd;

        public SimpleExecution(String cmd) {
            this.cmd = cmd;
        }

        @Override
        public boolean run(CommandSender sender, Player player, Player agent, Function<String, String> parser) {
            run(sender, player, agent, cmd, parser);
            return false;
        }
    }

    public static class AdvancedExecution extends Execution {
        private final double chance;
        private final boolean cancelling;
        private final String permission;
        private final List<Execution> executions;

        public AdvancedExecution(double chance, boolean cancelling, String permission, List<Execution> executions) {
            this.chance = chance;
            this.cancelling = cancelling;
            this.permission = permission;
            this.executions = executions;
        }

        public boolean testChance() {
            return chance == 0.0 || ThreadLocalRandom.current().nextDouble() <= chance;
        }

        public boolean meetPermission(Player player) {
            if (permission != null) for (String s : permission.split(";"))
                if (!player.hasPermission(s.trim())) return false;
            return true;
        }

        @Override
        public boolean run(CommandSender sender, Player player, Player agent, Function<String, String> parser) {
            if (!testChance() || !meetPermission(player)) return false;

            // Stop traversing this level if a child execution requests cancellation
            for (Execution execution : executions)
                if (execution.run(sender, player, agent, parser)) break;

            return cancelling;
        }
    }

    public enum Type {
        DEATH_PLAYER,
        DEATH_CONSOLE,
        KILL_PLAYER,
        KILL_CONSOLE,
        RESPAWN_PLAYER,
        RESPAWN_CONSOLE;

        public boolean isConsole() {
            return this == DEATH_CONSOLE || this == KILL_CONSOLE || this == RESPAWN_CONSOLE;
        }
    }
}
