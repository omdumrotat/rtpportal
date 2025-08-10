package com.omdmrotat.rtpportal;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Integration class for CombatLogX plugin to check combat status of players.
 */
public class CombatLogXIntegration {
    
    private final RTPPortal plugin;
    private ICombatLogX combatLogXAPI;
    private boolean isEnabled;
    
    public CombatLogXIntegration(RTPPortal plugin) {
        this.plugin = plugin;
        this.isEnabled = initializeCombatLogX();
    }
    
    /**
     * Initialize the CombatLogX API connection.
     * @return true if CombatLogX is available and API was successfully initialized
     */
    private boolean initializeCombatLogX() {
        PluginManager pluginManager = Bukkit.getPluginManager();
        
        if (!pluginManager.isPluginEnabled("CombatLogX")) {
            plugin.getLogger().info("CombatLogX not found. Combat checking will be disabled.");
            return false;
        }
        
        try {
            Plugin combatLogXPlugin = pluginManager.getPlugin("CombatLogX");
            if (combatLogXPlugin instanceof ICombatLogX) {
                this.combatLogXAPI = (ICombatLogX) combatLogXPlugin;
                plugin.getLogger().info("Successfully hooked into CombatLogX.");
                return true;
            } else {
                plugin.getLogger().warning("CombatLogX found but API interface not available.");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to hook into CombatLogX: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if CombatLogX integration is enabled and available.
     * @return true if CombatLogX is available
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * Check if a player is currently in combat.
     * @param player The player to check
     * @return true if the player is in combat, false otherwise
     */
    public boolean isInCombat(Player player) {
        if (!isEnabled || combatLogXAPI == null) {
            return false;
        }
        
        try {
            ICombatManager combatManager = combatLogXAPI.getCombatManager();
            return combatManager.isInCombat(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking combat status for player " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Filter out players who are in combat from the given set.
     * Also sends a message to players in combat explaining why they weren't teleported.
     * @param playerUUIDs Set of player UUIDs to filter
     * @return Set of player UUIDs who are not in combat
     */
    public Set<UUID> filterPlayersNotInCombat(Set<UUID> playerUUIDs) {
        if (!isEnabled || !plugin.getConfigManager().isPreventCombatTeleport()) {
            return new HashSet<>(playerUUIDs);
        }

        Set<UUID> playersNotInCombat = new HashSet<>();
        Set<UUID> playersToRemoveFromPortal = new HashSet<>();
        String combatMessage = plugin.getConfigManager().getCombatMessage();
        Component combatComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(combatMessage);
        boolean removeCombatPlayers = plugin.getConfigManager().isRemoveCombatPlayers();

        for (UUID uuid : playerUUIDs) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (isInCombat(player)) {
                    // Player is in combat, don't teleport them and send a message
                    player.sendMessage(combatComponent);
                    plugin.getLogger().info("Player " + player.getName() + " was not teleported due to being in combat.");

                    // If configured to remove combat players, mark them for removal
                    if (removeCombatPlayers) {
                        playersToRemoveFromPortal.add(uuid);
                    }
                } else {
                    // Player is not in combat, they can be teleported
                    playersNotInCombat.add(uuid);
                }
            } else {
                // Player is offline, remove from list
                plugin.getLogger().warning("Player with UUID " + uuid + " is offline, removing from teleport list.");
                playersToRemoveFromPortal.add(uuid);
            }
        }

        // Remove players from portal if configured to do so
        for (UUID uuid : playersToRemoveFromPortal) {
            plugin.getPlayersInPortal().remove(uuid);
        }

        return playersNotInCombat;
    }
    
    /**
     * Get the number of players in combat from the given set.
     * @param playerUUIDs Set of player UUIDs to check
     * @return Number of players in combat
     */
    public int getPlayersInCombatCount(Set<UUID> playerUUIDs) {
        if (!isEnabled) {
            return 0;
        }
        
        int count = 0;
        for (UUID uuid : playerUUIDs) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && isInCombat(player)) {
                count++;
            }
        }
        return count;
    }
}
