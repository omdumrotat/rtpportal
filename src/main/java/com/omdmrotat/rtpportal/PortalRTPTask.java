package com.omdmrotat.rtpportal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;


public class PortalRTPTask implements Runnable {

    private final RTPPortal plugin;

    public PortalRTPTask(RTPPortal plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Clean up offline players and players who are no longer in the region
        cleanupPlayersInPortal();

        if (plugin.getPlayersInPortal().isEmpty()) {
            plugin.getTeleportTimer().set(plugin.getConfigManager().getTeleportTimer());
            return;
        }

        int currentTime = plugin.getTeleportTimer().get();

        // Send countdown titles only to players who are actually in the portal region
        for (UUID playerUUID : new HashSet<>(plugin.getPlayersInPortal())) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && isPlayerInPortalRegion(player)) {
                sendCountdownTitle(player, currentTime);
            }
        }

        if (currentTime == 0) {
            // Filter out players who are in combat before teleporting
            Set<UUID> playersToTeleport = plugin.getCombatLogXIntegration()
                    .filterPlayersNotInCombat(plugin.getPlayersInPortal());

            if (!playersToTeleport.isEmpty()) {
                plugin.getTeleportManager().findAndTeleportPlayers(playersToTeleport);
            } else {
                plugin.getLogger().info("No players could be teleported - all players are either offline or in combat.");
            }
        }

        if (plugin.getTeleportTimer().decrementAndGet() < 0) {
            plugin.getTeleportTimer().set(plugin.getConfigManager().getTeleportTimer());
        }
    }

    private void sendCountdownTitle(Player player, int time) {
        if (time <= 0) return;
        /* donutsmp rtpzone title replica */
        final Component mainTitle = Component.text("ʀᴛᴘ ᴢᴏɴᴇ", NamedTextColor.RED);
        final Component subtitle = Component.text("Teleporting in " + time, NamedTextColor.WHITE);
        final Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1));
        final Title title = Title.title(mainTitle, subtitle, times);
        player.showTitle(title);
    }

    /**
     * Clean up players who are offline or no longer in the portal region
     */
    private void cleanupPlayersInPortal() {
        Set<UUID> playersToRemove = new HashSet<>();

        for (UUID playerUUID : plugin.getPlayersInPortal()) {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !isPlayerInPortalRegion(player)) {
                playersToRemove.add(playerUUID);
            }
        }

        // Remove players who are no longer valid
        for (UUID playerUUID : playersToRemove) {
            plugin.getPlayersInPortal().remove(playerUUID);
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                plugin.getLogger().info("Removed " + player.getName() + " from portal - no longer in region.");
            }
        }
    }

    /**
     * Check if a player is currently in the portal region
     */
    private boolean isPlayerInPortalRegion(Player player) {
        try {
            RegionManager regions = WorldGuard.getInstance().getPlatform()
                    .getRegionContainer().get(BukkitAdapter.adapt(player.getWorld()));

            if (regions == null) return false;

            ApplicableRegionSet regionSet = regions.getApplicableRegions(
                    BukkitAdapter.adapt(player.getLocation()).toVector().toBlockPoint());

            String regionId = plugin.getConfigManager().getRegionName();
            return regionSet.getRegions().stream()
                    .anyMatch(r -> r.getId().equalsIgnoreCase(regionId));
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking region for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

}