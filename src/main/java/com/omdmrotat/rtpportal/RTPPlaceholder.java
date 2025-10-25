package com.omdmrotat.rtpportal;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class RTPPlaceholder extends PlaceholderExpansion {

    private final RTPPortal plugin;

    public RTPPlaceholder(RTPPortal plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "rtpportal";
    }

    @Override
    public @NotNull String getAuthor() {
        return "omdmrotat";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // %rtpportal_timer% - Global timer
        if (params.equalsIgnoreCase("timer")) {
            return String.valueOf(plugin.getTeleportTimer().get());
        }
        
        // %rtpportal_region% - Current region the player is in (if online)
        if (params.equalsIgnoreCase("region")) {
            if (player != null && player.isOnline() && player.getPlayer() != null) {
                String region = plugin.getPlayerRegion(player.getUniqueId());
                return region != null ? region : "none";
            }
            return "none";
        }
        
        // %rtpportal_<worldkey>_region% - Get region name for a specific world
        // Example: %rtpportal_overworld_region%
        if (params.contains("_region")) {
            String worldKey = params.replace("_region", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return config.getRegionName();
            }
        }
        
        // %rtpportal_<worldkey>_world% - Get world name for a specific world config
        // Example: %rtpportal_overworld_world%
        if (params.contains("_world")) {
            String worldKey = params.replace("_world", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return config.getWorldName();
            }
        }
        
        // %rtpportal_<worldkey>_minx% - Get min X for a specific world
        if (params.contains("_minx")) {
            String worldKey = params.replace("_minx", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return String.valueOf(config.getMinX());
            }
        }
        
        // %rtpportal_<worldkey>_maxx% - Get max X for a specific world
        if (params.contains("_maxx")) {
            String worldKey = params.replace("_maxx", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return String.valueOf(config.getMaxX());
            }
        }
        
        // %rtpportal_<worldkey>_minz% - Get min Z for a specific world
        if (params.contains("_minz")) {
            String worldKey = params.replace("_minz", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return String.valueOf(config.getMinZ());
            }
        }
        
        // %rtpportal_<worldkey>_maxz% - Get max Z for a specific world
        if (params.contains("_maxz")) {
            String worldKey = params.replace("_maxz", "");
            ConfigManager.WorldRTPConfig config = plugin.getConfigManager().getWorldConfig(worldKey);
            if (config != null) {
                return String.valueOf(config.getMaxZ());
            }
        }
        
        return null;
    }
}