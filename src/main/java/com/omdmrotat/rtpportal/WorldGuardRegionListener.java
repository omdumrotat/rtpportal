package com.omdmrotat.rtpportal;

import java.time.Duration;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class WorldGuardRegionListener implements Listener {

    private final RTPPortal plugin;

    public WorldGuardRegionListener(RTPPortal plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Optimization: Don't run logic if the player hasn't moved to a new block
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

        // Cannot check regions if the region manager is null
        if (regions == null) return;

        // Get the regions at the old and new locations
        ApplicableRegionSet fromSet = regions.getApplicableRegions(BukkitAdapter.adapt(event.getFrom()).toVector().toBlockPoint());
        ApplicableRegionSet toSet = regions.getApplicableRegions(BukkitAdapter.adapt(event.getTo()).toVector().toBlockPoint());

        // Check all configured RTP regions
        for (String regionName : plugin.getConfigManager().getAllRegionNames()) {
            boolean wasInRegion = fromSet.getRegions().stream().anyMatch(r -> r.getId().equalsIgnoreCase(regionName));
            boolean isInRegion = toSet.getRegions().stream().anyMatch(r -> r.getId().equalsIgnoreCase(regionName));

            // Player entered a portal region
            if (isInRegion && !wasInRegion) {
                if (plugin.getPlayersInPortal().add(player.getUniqueId())) {
                    plugin.setPlayerRegion(player.getUniqueId(), regionName);
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
                    sendInitialTitle(player, regionName);
                }
            }
            // Player left a portal region
            else if (wasInRegion && !isInRegion) {
                // Only remove if they were in this specific region
                String playerCurrentRegion = plugin.getPlayerRegion(player.getUniqueId());
                if (regionName.equalsIgnoreCase(playerCurrentRegion)) {
                    if (plugin.getPlayersInPortal().remove(player.getUniqueId())) {
                        plugin.removePlayerRegion(player.getUniqueId());
                        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    /**
     * Sends the initial countdown title to a player upon entering the portal.
     * @param player The player to send the title to.
     * @param regionName The region the player entered.
     */
    private void sendInitialTitle(Player player, String regionName) {
        int currentTime = plugin.getTeleportTimer().get();
        if (currentTime <= 0) return;

        final Component mainTitle = Component.text("ʀᴛᴘ ᴢᴏɴᴇ", NamedTextColor.RED);
        final Component subtitle = Component.text("Teleporting in " + currentTime, NamedTextColor.WHITE);

        final Title.Times times = Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1));
        final Title title = Title.title(mainTitle, subtitle, times);

        player.showTitle(title);
    }
}