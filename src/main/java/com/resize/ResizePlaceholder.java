package com.resize;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class ResizePlaceholder extends PlaceholderExpansion {

    private final Resize plugin;

    public ResizePlaceholder(Resize plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "resize";
    }

    @Override
    public String getAuthor() {
        return "portal_gum";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {

        if (player == null) return "";

        boolean isAdmin = player.hasPermission("resize.admin");
        boolean canResize = player.hasPermission("resize.resize");
        boolean canMob = player.hasPermission("resize.mob");

        switch (identifier.toLowerCase()) {

            case "current_size":
                return String.valueOf(plugin.getCurrentSize(player));

            case "min_size":
                if (!canResize) return "-";

                if (isAdmin)
                    return plugin.msgNoPrefix("unlimited");

                return String.valueOf(plugin.getLimits(
                        player,
                        "scale.min",
                        "scale.max",
                        ".min",
                        ".max"
                )[0]);

            case "max_size":
                if (!canResize) return "-";

                if (isAdmin)
                    return plugin.msgNoPrefix("unlimited");

                return String.valueOf(plugin.getLimits(
                        player,
                        "scale.min",
                        "scale.max",
                        ".min",
                        ".max"
                )[1]);

            case "mob_min":
                if (!canMob) return "-";

                if (isAdmin)
                    return plugin.msgNoPrefix("unlimited");

                return String.valueOf(plugin.getLimits(
                        player,
                        "scale.mob_min",
                        "scale.mob_max",
                        ".mob_min",
                        ".mob_max"
                )[0]);

            case "mob_max":
                if (!canMob) return "-";

                if (isAdmin)
                    return plugin.msgNoPrefix("unlimited");

                return String.valueOf(plugin.getLimits(
                        player,
                        "scale.mob_min",
                        "scale.mob_max",
                        ".mob_min",
                        ".mob_max"
                )[1]);
        }

        return null;
    }

}