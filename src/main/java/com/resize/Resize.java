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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;


public class Resize extends JavaPlugin implements TabExecutor, Listener {


    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> animationTasks = new ConcurrentHashMap<>();
    private final Set<UUID> animating = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> resizeRegionCache = new HashMap<>();
    private String msg(String path) {
        String prefix = color(getConfig().getString("prefix", ""));
        String message = lang.getString(path, "&cMissing lang key: " + path);
        return prefix + color(message);
    }
    private String msgNoPrefix(String path) {
        String message = lang.getString(path, "&cMissing lang key: " + path);
        return color(message);
    }

    private FileConfiguration lang;
    private BooleanFlag RESIZE_FLAG;
    private boolean worldGuardEnabled = false;


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

        // bStats
        int pluginId = 29522;
        Metrics metrics = new Metrics(this, pluginId);

        // Group limits enabled / disabled
        metrics.addCustomChart(new SimplePie("group_limits_enabled", () ->
                getConfig().getBoolean("group-limits.enabled")
                        ? "Enabled"
                        : "Disabled"
        ));

        // Selected language
        metrics.addCustomChart(new SimplePie("selected_language", () ->
                getConfig().getString("lang", "en")
        ));

        // WorldGuard hook
        metrics.addCustomChart(new SimplePie("worldguard_hook", () ->
                getServer().getPluginManager().getPlugin("WorldGuard") != null
                        ? "Installed"
                        : "Not Installed"
        ));

        // Global minimum size
        metrics.addCustomChart(new SimplePie("global_min_size", () ->
                String.valueOf(getConfig().getDouble("scale.min", 0.6))
        ));

        // Global maximum size
        metrics.addCustomChart(new SimplePie("global_max_size", () ->
                String.valueOf(getConfig().getDouble("scale.max", 1.6))
        ));

        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardEnabled = true;
            getLogger().info("WorldGuard detected. Resize flag support enabled.");
        } else {
            getLogger().info("WorldGuard not found. Resize flag support disabled.");
        }
        startMobRegionTask();
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

    private double[] getMobLimits(Player player) {

        double globalMin = getConfig().getDouble("scale.mob_min", 0.5);
        double globalMax = getConfig().getDouble("scale.mob_max", 2.0);

        if (player.hasPermission("resize.admin")) {
            return new double[]{globalMin, globalMax};
        }

        if (!getConfig().getBoolean("group-limits.enabled", false)) {
            return new double[]{globalMin, globalMax};
        }

        String group = getPrimaryGroup(player);
        String path = "group-limits." + group;

        if (!getConfig().contains(path)) {
            return new double[]{globalMin, globalMax};
        }

        double min = getConfig().contains(path + ".mob_min")
                ? getConfig().getDouble(path + ".mob_min")
                : globalMin;

        double max = getConfig().contains(path + ".mob_max")
                ? getConfig().getDouble(path + ".mob_max")
                : globalMax;

        return new double[]{min, max};
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

        if (args.length >= 1 && args[0].equalsIgnoreCase("info")) {

            if (!(sender instanceof Player viewer)) {
                sender.sendMessage(msg("only-player"));
                return true;
            }

            if (!viewer.hasPermission("resize.info") && !viewer.hasPermission("resize.admin")) {
                viewer.sendMessage(msg("no-permission"));
                return true;
            }

            Player target = viewer;

            if (args.length == 2) {

                if (!viewer.hasPermission("resize.info.other") && !viewer.hasPermission("resize.admin")) {
                    viewer.sendMessage(msg("no-permission-target"));
                    return true;
                }

                target = Bukkit.getPlayer(args[1]);

                if (target == null) {
                    viewer.sendMessage(msg("player-not-found"));
                    return true;
                }
            }

            AttributeInstance scale = target.getAttribute(SCALE_ATTRIBUTE);
            if (scale == null) {
                viewer.sendMessage(msg("no-scale-support"));
                return true;
            }

            boolean isAdmin = target.hasPermission("resize.admin");

            viewer.sendMessage(color("&8&m-------------------------"));

            if (viewer != target) {
                viewer.sendMessage(msgNoPrefix("info-player")
                        .replace("{player}", target.getName()));
            } else {
                viewer.sendMessage(msgNoPrefix("info-self"));
            }

            viewer.sendMessage(msgNoPrefix("info-current")
                    .replace("{size}", String.valueOf(scale.getBaseValue())));

            // PLAYER LIMITS
            if (isAdmin) {
                viewer.sendMessage(msgNoPrefix("info-min-unlimited"));
                viewer.sendMessage(msgNoPrefix("info-max-unlimited"));
            } else {
                double[] playerLimits = getGroupLimits(target);

                viewer.sendMessage(msgNoPrefix("info-min")
                        .replace("{min}", String.valueOf(playerLimits[0])));
                viewer.sendMessage(msgNoPrefix("info-max")
                        .replace("{max}", String.valueOf(playerLimits[1])));
            }

            // MOB LIMITS
            viewer.sendMessage(color("&7"));

            if (isAdmin) {
                viewer.sendMessage(msgNoPrefix("info-mob-min-unlimited"));
                viewer.sendMessage(msgNoPrefix("info-mob-max-unlimited"));
            } else {
                double[] mobLimits = getMobLimits(target);

                viewer.sendMessage(msgNoPrefix("info-mob-min")
                        .replace("{min}", String.valueOf(mobLimits[0])));
                viewer.sendMessage(msgNoPrefix("info-mob-max")
                        .replace("{max}", String.valueOf(mobLimits[1])));
            }

            viewer.sendMessage(color("&8&m-------------------------"));

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

        // /resize mob <size>
        if (args.length >= 2 && args[0].equalsIgnoreCase("mob")) {

            if (!player.hasPermission("resize.mob") && !player.hasPermission("resize.admin")) {
                player.sendMessage(msg("no-permission"));
                return true;
            }

            if (!player.hasPermission("resize.admin") && !isWorldAllowed(player)) {
                player.sendMessage(msg("world-disabled"));
                return true;
            }

            double size;
            try {
                size = Double.parseDouble(args[1].replace(',', '.'));
            } catch (NumberFormatException e) {
                player.sendMessage(msg("not-number"));
                return true;
            }

            size = Math.floor(size * 10.0) / 10.0;

            // if the mob is in the crosshairs
            double maxDistance = getConfig().getDouble("mob.distance", 7);

            org.bukkit.entity.Entity target = player.getTargetEntity((int) maxDistance);

            if (!(target instanceof org.bukkit.entity.LivingEntity living) || target instanceof Player) {
                player.sendMessage(msg("mob-not-found"));
                return true;
            }

            if (player.getLocation().distance(target.getLocation()) > maxDistance) {
                player.sendMessage(msg("mob-too-far")
                        .replace("{distance}", String.valueOf(maxDistance)));
                return true;
            }

            double[] limits = getMobLimits(player);

            if (!player.hasPermission("resize.admin")) {
                if (size < limits[0] || size > limits[1]) {
                    player.sendMessage(msg("mob-size-limit")
                            .replace("{min}", String.valueOf(limits[0]))
                            .replace("{max}", String.valueOf(limits[1])));
                    return true;
                }
            }

            // cooldown (shared)
            if (!player.hasPermission("resize.admin")) {

                long now = System.currentTimeMillis();
                long lastUse = cooldowns.getOrDefault(player.getUniqueId(), 0L);

                if (now - lastUse < cooldownSeconds * 1000L) {
                    long remaining =
                            ((cooldownSeconds * 1000L) - (now - lastUse)) / 1000;

                    player.sendMessage(msg("cooldown")
                            .replace("{time}", String.valueOf(remaining)));
                    return true;
                }

                cooldowns.put(player.getUniqueId(), now);
            }

            AttributeInstance scale = living.getAttribute(SCALE_ATTRIBUTE);
            if (scale == null) {
                player.sendMessage(msg("no-scale-support"));
                return true;
            }


            boolean animation = getConfig().getBoolean("animation.enabled", true);

            if (animation) {
                animateMobScale(living, size);
            } else {
                scale.setBaseValue(size);
            }

            player.sendMessage(msg("mob-resized")
                    .replace("{size}", String.valueOf(size)));

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

        if (!isAdmin && !isResizeAllowed(target)) {
            player.sendMessage(msg("region-disabled"));
            return true;
        }

        // limitation
        if (!isAdmin) {

            // size limits
            double[] limits = getGroupLimits(player);
            double minSize = limits[0];
            double maxSize = limits[1];

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

        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();

        // first arg
        if (args.length == 1) {

            // info
            if (player.hasPermission("resize.info") || player.hasPermission("resize.admin")) {
                list.add("info");
            }

            // reload
            if (player.hasPermission("resize.reload")) {
                list.add("reload");
            }

            // mob
            if (player.hasPermission("resize.mob") || player.hasPermission("resize.admin")) {
                list.add("mob");
            }

            if (player.hasPermission("resize.resize") || player.hasPermission("resize.admin")) {

                double minSize = getConfig().getDouble("scale.min", 0.6);
                double maxSize = getConfig().getDouble("scale.max", 1.6);

                if (!player.hasPermission("resize.admin")) {
                    double[] limits = getGroupLimits(player);
                    minSize = limits[0];
                    maxSize = limits[1];
                }

                list.add(String.valueOf(minSize));
                list.add(String.valueOf(maxSize));
            }

            return list;
        }

        // /resize mob <size>
        if (args.length == 2 && args[0].equalsIgnoreCase("mob")) {

            if (!player.hasPermission("resize.mob") && !player.hasPermission("resize.admin")) {
                return Collections.emptyList();
            }

            double min = getConfig().getDouble("scale.mob_min", 0.5);
            double max = getConfig().getDouble("scale.mob_max", 2.0);

            if (!player.hasPermission("resize.admin")) {
                double[] limits = getMobLimits(player);
                min = limits[0];
                max = limits[1];
            }


            list.add(String.valueOf(min));
            list.add(String.valueOf(max));


            return list;
        }

        // /resize <size> <player>
        if (args.length == 2 && !args[0].equalsIgnoreCase("mob")
                && (player.hasPermission("resize.admin"))) {

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

    private String color(String text) {
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

    private void animateMobScale(org.bukkit.entity.LivingEntity entity, double targetScale) {

        AttributeInstance scaleAttr = entity.getAttribute(SCALE_ATTRIBUTE);
        if (scaleAttr == null) return;

        double start = scaleAttr.getBaseValue();
        double duration = getConfig().getDouble("animation.duration", 2.0);

        int ticks = Math.max(1, (int) Math.round(duration * 20));
        double step = (targetScale - start) / ticks;

        new BukkitRunnable() {

            int currentTick = 0;

            @Override
            public void run() {

                if (!entity.isValid()) {
                    cancel();
                    return;
                }

                currentTick++;
                double newScale = start + (step * currentTick);
                scaleAttr.setBaseValue(newScale);

                if (currentTick >= ticks) {
                    scaleAttr.setBaseValue(targetScale);
                    cancel();
                }
            }

        }.runTaskTimer(this, 0L, 1L);
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

    private String getPrimaryGroup(Player player) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            return api.getUserManager()
                    .getUser(player.getUniqueId())
                    .getPrimaryGroup();
        } catch (Exception e) {
            return "default";
        }
    }


    private double[] getGroupLimits(Player player) {

        double globalMin = getConfig().getDouble("scale.min", 0.6);
        double globalMax = getConfig().getDouble("scale.max", 1.6);

        if (player.hasPermission("resize.admin")) {
            return new double[]{globalMin, globalMax};
        }

        if (!getConfig().getBoolean("group-limits.enabled", false)) {
            return new double[]{globalMin, globalMax};
        }

        String group = getPrimaryGroup(player);

        if (group == null) {
            return new double[]{globalMin, globalMax};
        }

        String path = "group-limits." + group;

        if (!getConfig().contains(path)) {
            return new double[]{globalMin, globalMax};
        }

        double min = getConfig().contains(path + ".min")
                ? getConfig().getDouble(path + ".min")
                : globalMin;

        double max = getConfig().contains(path + ".max")
                ? getConfig().getDouble(path + ".max")
                : globalMax;

        return new double[]{min, max};
    }

    @Override
    public void onLoad() {

        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }

        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            BooleanFlag flag = new BooleanFlag("resize");
            registry.register(flag);
            RESIZE_FLAG = flag;

            getLogger().info("WorldGuard flag registered: resize");

        } catch (Exception e) {

            Flag<?> existing = WorldGuard.getInstance()
                    .getFlagRegistry()
                    .get("resize");

            if (existing instanceof BooleanFlag) {
                RESIZE_FLAG = (BooleanFlag) existing;
                getLogger().info("Using existing WorldGuard flag: resize");
            }
        }
    }

    private boolean isResizeAllowed(Player player) {

        if (!worldGuardEnabled || RESIZE_FLAG == null) {
            return true;
        }

        if (player.hasPermission("resize.admin")) {
            return true;
        }

        RegionContainer container = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();

        RegionQuery query = container.createQuery();

        ApplicableRegionSet regions = query.getApplicableRegions(
                BukkitAdapter.adapt(player.getLocation())
        );

        Boolean result = regions.queryValue(
                WorldGuardPlugin.inst().wrapPlayer(player),
                RESIZE_FLAG
        );

        if (result == null) {
            return true;
        }

        return result;
    }

    private boolean isResizeAllowed(org.bukkit.entity.Entity entity) {

        if (!worldGuardEnabled || RESIZE_FLAG == null) {
            return true;
        }

        RegionContainer container = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();

        RegionQuery query = container.createQuery();

        ApplicableRegionSet regions = query.getApplicableRegions(
                BukkitAdapter.adapt(entity.getLocation())
        );

        Boolean result = regions.queryValue(null, RESIZE_FLAG);

        if (result == null) {
            return true;
        }

        return result;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!worldGuardEnabled || RESIZE_FLAG == null) return;
        if (player.hasPermission("resize.admin")) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        boolean allowedNow = isResizeAllowed(player);

        Boolean previous = resizeRegionCache.get(player.getUniqueId());

        if (previous != null && previous == allowedNow) {
            return;
        }

        resizeRegionCache.put(player.getUniqueId(), allowedNow);

        if (!allowedNow) {

            AttributeInstance scale = player.getAttribute(SCALE_ATTRIBUTE);
            if (scale != null && scale.getBaseValue() != 1.0) {

                resetSize(player);
                player.sendMessage(msg("region-size-reset"));
            }
        }
    }

    private void startMobRegionTask() {

        if (!worldGuardEnabled || RESIZE_FLAG == null) return;

        new BukkitRunnable() {

            @Override
            public void run() {

                for (org.bukkit.World world : Bukkit.getWorlds()) {

                    for (org.bukkit.entity.LivingEntity living : world.getLivingEntities()) {

                        if (living instanceof Player) continue;

                        AttributeInstance scale = living.getAttribute(SCALE_ATTRIBUTE);
                        if (scale == null) continue;

                        if (scale.getBaseValue() == 1.0) continue;

                        if (!isResizeAllowed(living)) {
                            scale.setBaseValue(1.0);
                        }
                    }
                }
            }

        }.runTaskTimer(this, 40L, 40L); // каждые 2 секунды
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resizeRegionCache.remove(event.getPlayer().getUniqueId());
    }


}