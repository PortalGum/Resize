package com.resize;

import com.resize.Resize;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;

public class WorldGuardHook {

    private final Resize plugin;
    private StateFlag resizeFlag;

    public WorldGuardHook(Resize plugin) {
        this.plugin = plugin;
        registerFlag();
    }

    private void registerFlag() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
            StateFlag flag = new StateFlag("resize", true);
            registry.register(flag);
            resizeFlag = flag;
            plugin.getLogger().info("WorldGuard flag registered: resize");
        } catch (Exception e) {
            Flag<?> existing = WorldGuard.getInstance()
                    .getFlagRegistry()
                    .get("resize");

            if (existing instanceof StateFlag) {
                resizeFlag = (StateFlag) existing;
                plugin.getLogger().info("Using existing WorldGuard flag: resize");
            }
        }
    }

    public boolean isResizeAllowed(Player player) {
        if (resizeFlag == null) return true;

        RegionContainer container = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();

        RegionQuery query = container.createQuery();

        ApplicableRegionSet regions = query.getApplicableRegions(
                BukkitAdapter.adapt(player.getLocation())
        );

        StateFlag.State state = regions.queryState(
                WorldGuardPlugin.inst().wrapPlayer(player),
                resizeFlag
        );

        return state != StateFlag.State.DENY;
    }

    public boolean isResizeAllowed(Entity entity) {
        if (resizeFlag == null) return true;

        RegionContainer container = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer();

        RegionQuery query = container.createQuery();

        ApplicableRegionSet regions = query.getApplicableRegions(
                BukkitAdapter.adapt(entity.getLocation())
        );

        StateFlag.State state = regions.queryState(null, resizeFlag);

        return state != StateFlag.State.DENY;
    }
}