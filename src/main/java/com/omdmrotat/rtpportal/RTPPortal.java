package com.omdmrotat.rtpportal;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class RTPPortal extends JavaPlugin {
    private ConfigManager configManager;
    private static RTPPortal instance;
    private TeleportManager teleportManager;
    private CombatLogXIntegration combatLogXIntegration;
    private static boolean isFolia = false;

    private final Set<UUID> playersInPortal = Collections.synchronizedSet(new HashSet<>());
    // Map to track which region each player is in: UUID -> regionName
    private final Map<UUID, String> playerRegionMap = Collections.synchronizedMap(new HashMap<>());
    private final AtomicInteger teleportTimer = new AtomicInteger();

    private RegionContainer regionContainer;
    private ScheduledTask portalTask;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        teleportManager = new TeleportManager(this);
        combatLogXIntegration = new CombatLogXIntegration(this);

        // Initialize timer with configured value
        teleportTimer.set(configManager.getTeleportTimer());
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
            getLogger().info("Folia detected. Running with Folia support.");
        } catch (ClassNotFoundException e) {
            isFolia = false;
            getLogger().info("Folia not detected. Running in standard Bukkit/Paper mode.");
        }


        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("WorldGuard not found! This plugin is required.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();


        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new WorldGuardRegionListener(this), this);


        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RTPPlaceholder(this).register();
            getLogger().info("Successfully hooked into PlaceholderAPI.");
        }


        AsyncScheduler scheduler = getServer().getAsyncScheduler();


        PortalRTPTask portalRunnable = new PortalRTPTask(this);


        portalTask = scheduler.runAtFixedRate(this, task -> portalRunnable.run(), 0, 1, TimeUnit.SECONDS);

        // Schedule cache cleanup every 5 minutes
        scheduler.runAtFixedRate(this, task -> teleportManager.cleanupCaches(), 5, 5, TimeUnit.MINUTES);

        getLogger().info("RTPPortal has been enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (portalTask  != null) {
            portalTask.cancel();
        }
        playersInPortal.clear();
        playerRegionMap.clear();
        getLogger().info("RTPPortal has been disabled.");
    }

    public static RTPPortal getInstance() {
        return instance;
    }

    public static boolean isFolia() {
        return isFolia;
    }

    public Set<UUID> getPlayersInPortal() {
        return playersInPortal;
    }
    
    public Map<UUID, String> getPlayerRegionMap() {
        return playerRegionMap;
    }
    
    public String getPlayerRegion(UUID playerUUID) {
        return playerRegionMap.get(playerUUID);
    }
    
    public void setPlayerRegion(UUID playerUUID, String regionName) {
        playerRegionMap.put(playerUUID, regionName);
    }
    
    public void removePlayerRegion(UUID playerUUID) {
        playerRegionMap.remove(playerUUID);
    }

    public AtomicInteger getTeleportTimer() {
        return teleportTimer;
    }

    public RegionContainer getRegionContainer() {
        return regionContainer;
    }
    public ConfigManager getConfigManager() {
        return configManager;
    }
    public TeleportManager getTeleportManager() {
        return teleportManager;
    }

    public CombatLogXIntegration getCombatLogXIntegration() {
        return combatLogXIntegration;
    }
}