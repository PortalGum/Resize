package com.resize;

import org.bukkit.Bukkit;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class Resize extends JavaPlugin implements TabExecutor, Listener {


    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> animationTasks = new ConcurrentHashMap<>();
    private final Set<UUID> animating = ConcurrentHashMap.newKeySet();
    private String msg(String path) {
        String prefix = color(getConfig().getString("prefix", ""));
        String message = lang.getString(path, "&cMissing lang key: " + path);
        return prefix + color(message);
    }

    private FileConfiguration lang;


    @Override
    public void onEnable() {
        detectScaleAttribute();

        if (SCALE_ATTRIBUTE == null) {
            getLogger().severe("Disabling plugin: no scale attribute support.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadLang();
        getCommand("resize").setExecutor(this);
        getCommand("resize").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private void loadLang() {

        String langName = getConfig().getString("lang", "en");

        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }

        File file = new File(langDir, langName + ".yml");

        if (!file.exists()) {
            saveResource("lang/" + langName + ".yml", false);
        }

        lang = YamlConfiguration.loadConfiguration(file);
    }

    private void resetSize(Player player) {

        UUID uuid = player.getUniqueId();

        // stop animation
        if (animationTasks.containsKey(uuid)) {
            animationTasks.get(uuid).cancel();
        }

        animating.remove(uuid);

        AttributeInstance scale = player.getAttribute(SCALE_ATTRIBUTE);
        if (scale != null) {
            scale.setBaseValue(1.0);
        }
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String prefix = color(getConfig().getString("prefix", "&e[&aResize&e]&r "));
        int cooldownSeconds = getConfig().getInt("cooldown", 3);

        if (!(sender instanceof Player)) {

            // exception /resize reload
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadLang();
                sender.sendMessage(msg("reload-success"));
                return true;
            }

            sender.sendMessage(msg("only-player"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("resize.resize")) {
            return true;
        }

        // /resize reload
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {

            if (!player.hasPermission("resize.reload")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }

            reloadConfig();
            loadLang();
            player.sendMessage(msg("reload-success"));

            return true;
        }

        boolean isAdmin = player.hasPermission("resize.admin");

        if (args.length < 1) {
            player.sendMessage(
                    player.hasPermission("resize.admin")
                            ? msg("usage-admin")
                            : msg("usage-player")
            );
            return true;
        }


        double size;
        try {
            String input = args[0].replace(',', '.');

            try {
                size = Double.parseDouble(input);
            } catch (NumberFormatException e) {
                player.sendMessage(msg("not-number"));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(msg("not-number"));
            return true;
        }

        // rounds off numbers after the decimal point
        size = Math.floor(size * 10.0) / 10.0;

        Player target = player;

        // if player is specified
        if (args.length >= 2) {
            if (!isAdmin) {
                player.sendMessage(msg("no-permission-target"));

                return true;
            }

            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(msg("player-not-found"));
                return true;
            }
        }

        if (!isAdmin && !isWorldAllowed(target)) {
            player.sendMessage(msg("world-disabled"));
            return true;
        }

        // limitation
        if (!isAdmin) {

            // size limits
            double minSize = getConfig().getDouble("scale.min", 0.6);
            double maxSize = getConfig().getDouble("scale.max", 1.6);

            if (size < minSize || size > maxSize) {
                player.sendMessage(msg("size-limit")
                        .replace("{min}", String.valueOf(minSize))
                        .replace("{max}", String.valueOf(maxSize)));

                return true;
            }

            // cooldown
            long now = System.currentTimeMillis();
            long lastUse = cooldowns.getOrDefault(player.getUniqueId(), 0L);

            if (now - lastUse < cooldownSeconds * 1000L) {
                long remaining = ((cooldownSeconds * 1000L) - (now - lastUse)) / 1000;
                player.sendMessage(msg("cooldown")
                        .replace("{time}", String.valueOf(remaining)));

                return true;
            }

            cooldowns.put(player.getUniqueId(), now);
        }

        // apply size
        AttributeInstance scale = target.getAttribute(SCALE_ATTRIBUTE);

        // check
        if (scale == null) {
            player.sendMessage(msg("no-scale-support"));
            return true;
        }

        if (Math.abs(scale.getBaseValue() - size) < 0.001) {

            if (target == player) {
                player.sendMessage(msg("already-this-size"));
            } else {
                player.sendMessage(
                        msg("player-already-this-size")
                                .replace("{player}", target.getName())
                );
            }

            return true;
        }



        boolean animation = getConfig().getBoolean("animation.enabled", true);

        if (animation) {
            animateScale(target, size);
        } else {
            scale.setBaseValue(size);
        }


        if (target == player) {
            player.sendMessage(msg("self-resize")
                    .replace("{size}", String.valueOf(size)));
        } else {
            player.sendMessage(msg("other-resize")
                    .replace("{player}", target.getName())
                    .replace("{size}", String.valueOf(size)));

            target.sendMessage(msg("target-message")
                    .replace("{size}", String.valueOf(size)));
        }

        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean saveSizes = getConfig().getBoolean("save-sizes", true);

        if (!saveSizes) {
            AttributeInstance scale = player.getAttribute(SCALE_ATTRIBUTE);
            if (scale != null) {
                resetSize(player);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        // if world is forbidden
        if (!player.hasPermission("resize.admin") && !isWorldAllowed(player)) {


            AttributeInstance scale = player.getAttribute(SCALE_ATTRIBUTE);
            if (scale != null && scale.getBaseValue() != 1.0) {

                resetSize(player);
                player.sendMessage(msg("world-size-reset"));
            }
        }
    }

    private boolean isWorldAllowed(Player player) {

        String mode = getConfig().getString("worlds.mode", "blacklist");
        List<String> worlds = getConfig().getStringList("worlds.list");

        String worldName = player.getWorld().getName();

        if (mode.equalsIgnoreCase("whitelist")) {
            return worlds.contains(worldName);
        }

        // blacklist default
        return !worlds.contains(worldName);
    }


    // tabcompleter

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> list = new ArrayList<>();

            if (sender.hasPermission("resize.reload")) {
                list.add("reload");
            }

            double minSize = getConfig().getDouble("scale.min", 0.6);
            double maxSize = getConfig().getDouble("scale.max", 1.6);

            list.add(String.valueOf(minSize));
            list.add(String.valueOf(maxSize));

            return list;
        }

        if (args.length == 2 && sender.hasPermission("resize.admin")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }

        return Collections.emptyList();
    }

    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private String g(String text) {
        if (text == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            ChatColor color = ChatColor.of("#" + matcher.group(1));
            matcher.appendReplacement(buffer, color.toString());
        }

        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }


    @EventHandler
    public void onDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        AttributeInstance scale = victim.getAttribute(SCALE_ATTRIBUTE);
        if (scale == null || scale.getBaseValue() == 1.0) {
            return;
        }

        boolean willDie = event.getFinalDamage() >= victim.getHealth();

        // if player dies
        if (willDie && !getConfig().getBoolean("reset-on-damage.player.death")) {
            return;
        }

        boolean reset = false;

        if (event instanceof EntityDamageByEntityEvent entityEvent) {

            if (entityEvent.getDamager() instanceof Player) {
                reset = getConfig().getBoolean("reset-on-damage.player.receive");
            } else if (entityEvent.getDamager() instanceof org.bukkit.entity.LivingEntity) {
                reset = getConfig().getBoolean("reset-on-damage.mob.receive");
            } else {
                reset = getConfig().getBoolean("reset-on-damage.other.receive");
            }

        } else {
            reset = getConfig().getBoolean("reset-on-damage.other.receive");
        }

        boolean isAdmin = victim.hasPermission("resize.admin");
        boolean adminReset = getConfig().getBoolean("admin-reset", false);

        if (reset && (!isAdmin || adminReset)) {
            resetSize(victim);
            victim.sendMessage(msg("reset-receive"));

        }
    }

    @EventHandler
    public void onDealDamage(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        AttributeInstance scale = attacker.getAttribute(SCALE_ATTRIBUTE);
        if (scale == null || scale.getBaseValue() == 1.0) {
            return;
        }

        boolean reset;

        if (event.getEntity() instanceof Player) {
            reset = getConfig().getBoolean("reset-on-damage.player.deal");
        } else if (event.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            reset = getConfig().getBoolean("reset-on-damage.mob.deal");
        } else {
            reset = getConfig().getBoolean("reset-on-damage.other.deal");
        }

        boolean isAdmin = attacker.hasPermission("resize.admin");
        boolean adminReset = getConfig().getBoolean("admin-reset", false);

        if (reset && (!isAdmin || adminReset)) {
            resetSize(attacker);
            attacker.sendMessage(msg("reset-deal"));

        }

    }


    private void animateScale(Player player, double targetScale) {

        UUID uuid = player.getUniqueId();

        // If there is already an animation delete it.
        if (animationTasks.containsKey(uuid)) {
            animationTasks.get(uuid).cancel();
        }

        animating.add(uuid);

        AttributeInstance scaleAttr = player.getAttribute(SCALE_ATTRIBUTE);
        if (scaleAttr == null) return;

        double start = scaleAttr.getBaseValue();
        double duration = getConfig().getDouble("animation.duration", 2.0);

        int ticks = Math.max(1, (int) Math.round(duration * 20));
        double step = (targetScale - start) / ticks;

        BukkitRunnable task = new BukkitRunnable() {

            int currentTick = 0;

            @Override
            public void run() {

                if (!animating.contains(uuid) || !player.isOnline()) {
                    cancel();
                    return;
                }

                currentTick++;
                double newScale = start + (step * currentTick);
                scaleAttr.setBaseValue(newScale);

                if (currentTick >= ticks) {
                    scaleAttr.setBaseValue(targetScale);
                    cancel();
                    animating.remove(uuid);
                    animationTasks.remove(uuid);
                }
            }

            @Override
            public synchronized void cancel() throws IllegalStateException {
                super.cancel();
                animating.remove(uuid);
                animationTasks.remove(uuid);
            }
        };

        animationTasks.put(uuid, task);
        task.runTaskTimer(this, 0L, 1L);
    }

    private Attribute SCALE_ATTRIBUTE;

    private void detectScaleAttribute() {

        // 1.21+
        SCALE_ATTRIBUTE = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        if (SCALE_ATTRIBUTE != null) {
            getLogger().info("Using attribute: minecraft:scale");
            return;
        }

        // 1.20.5 - 1.20.6
        SCALE_ATTRIBUTE = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
        if (SCALE_ATTRIBUTE != null) {
            getLogger().info("Using attribute: minecraft:generic.scale");
            return;
        }

        // Not supported
        SCALE_ATTRIBUTE = null;
        getLogger().severe("Scale attribute is not supported on this server version!");
    }

}