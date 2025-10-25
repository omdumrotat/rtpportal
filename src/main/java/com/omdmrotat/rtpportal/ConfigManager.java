package com.omdmrotat.rtpportal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final RTPPortal plugin;
    private FileConfiguration config;

    // Map to store world configurations: worldKey -> WorldRTPConfig
    private Map<String, WorldRTPConfig> worldConfigs;
    // Map region name to world key for quick lookups
    private Map<String, String> regionToWorld;
    
    private Set<Material> safeBlockTypes;
    private int teleportTimer;

    // Water detection settings
    private boolean waterDetectionEnabled;
    private int waterCheckRadius;
    private double maxWaterPercentage;
    private boolean checkWaterAbove;
    private int checkAboveHeight;
    private boolean avoidOceanBiomes;

    // Performance optimization settings
    private int batchSize;
    private int maxBatchAttempts;
    private boolean smartHeightScan;
    private boolean biomePrefilter;

    // CombatLogX integration settings
    private boolean preventCombatTeleport;
    private String combatMessage;
    private boolean removeCombatPlayers;

    public ConfigManager(RTPPortal plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        worldConfigs = new HashMap<>();
        regionToWorld = new HashMap<>();

        // Load world configurations
        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection != null) {
            for (String worldKey : worldsSection.getKeys(false)) {
                ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldKey);
                if (worldSection != null) {
                    String regionName = worldSection.getString("region-name");
                    String worldName = worldSection.getString("teleport-bounds.world");
                    int minX = worldSection.getInt("teleport-bounds.x.min", -1000);
                    int maxX = worldSection.getInt("teleport-bounds.x.max", 1000);
                    int minZ = worldSection.getInt("teleport-bounds.z.min", -1000);
                    int maxZ = worldSection.getInt("teleport-bounds.z.max", 1000);

                    WorldRTPConfig worldConfig = new WorldRTPConfig(
                            worldKey, regionName, worldName, minX, maxX, minZ, maxZ
                    );
                    worldConfigs.put(worldKey, worldConfig);
                    regionToWorld.put(regionName.toLowerCase(), worldKey);

                    plugin.getLogger().info("Loaded world config: " + worldKey + 
                            " (region: " + regionName + ", world: " + worldName + ")");
                }
            }
        }

        if (worldConfigs.isEmpty()) {
            plugin.getLogger().warning("No world configurations found in config.yml!");
        }

        teleportTimer = config.getInt("teleport-timer", 30);

        List<String> materialNames = config.getStringList("safe-block-types");
        safeBlockTypes = materialNames.stream()
                .map(Material::matchMaterial)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (safeBlockTypes.isEmpty()) {
            plugin.getLogger().warning("No valid safe block types found in config.yml!");
        }

        // Load water detection settings
        waterDetectionEnabled = config.getBoolean("water-detection.enabled", true);
        waterCheckRadius = config.getInt("water-detection.check-radius", 3);
        maxWaterPercentage = config.getDouble("water-detection.max-water-percentage", 0.3);
        checkWaterAbove = config.getBoolean("water-detection.check-above", true);
        checkAboveHeight = config.getInt("water-detection.check-above-height", 5);
        avoidOceanBiomes = config.getBoolean("water-detection.avoid-ocean-biomes", true);

        // Load optimization settings
        batchSize = config.getInt("optimization.batch-size", 5);
        maxBatchAttempts = config.getInt("optimization.max-batch-attempts", 10);
        smartHeightScan = config.getBoolean("optimization.smart-height-scan", true);
        biomePrefilter = config.getBoolean("optimization.biome-prefilter", true);

        // Load CombatLogX integration settings
        preventCombatTeleport = config.getBoolean("combatlogx.prevent-combat-teleport", true);
        combatMessage = config.getString("combatlogx.combat-message", "&cYou cannot be teleported while in combat!");
        removeCombatPlayers = config.getBoolean("combatlogx.remove-combat-players", false);
    }

    // Getters for all the configuration values
    public Map<String, WorldRTPConfig> getWorldConfigs() { return worldConfigs; }
    
    public WorldRTPConfig getWorldConfigByRegion(String regionName) {
        String worldKey = regionToWorld.get(regionName.toLowerCase());
        return worldKey != null ? worldConfigs.get(worldKey) : null;
    }
    
    public WorldRTPConfig getWorldConfig(String worldKey) {
        return worldConfigs.get(worldKey);
    }
    
    public Set<String> getAllRegionNames() {
        return regionToWorld.keySet();
    }
    
    public Set<Material> getSafeBlockTypes() { return safeBlockTypes; }
    public int getTeleportTimer() { return teleportTimer; }
    
    // Inner class to hold world-specific configuration
    public static class WorldRTPConfig {
        private final String worldKey;
        private final String regionName;
        private final String worldName;
        private final int minX, maxX, minZ, maxZ;

        public WorldRTPConfig(String worldKey, String regionName, String worldName, 
                            int minX, int maxX, int minZ, int maxZ) {
            this.worldKey = worldKey;
            this.regionName = regionName;
            this.worldName = worldName;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        public String getWorldKey() { return worldKey; }
        public String getRegionName() { return regionName; }
        public String getWorldName() { return worldName; }
        public int getMinX() { return minX; }
        public int getMaxX() { return maxX; }
        public int getMinZ() { return minZ; }
        public int getMaxZ() { return maxZ; }
    }

    // Water detection getters
    public boolean isWaterDetectionEnabled() { return waterDetectionEnabled; }
    public int getWaterCheckRadius() { return waterCheckRadius; }
    public double getMaxWaterPercentage() { return maxWaterPercentage; }
    public boolean isCheckWaterAbove() { return checkWaterAbove; }
    public int getCheckAboveHeight() { return checkAboveHeight; }
    public boolean isAvoidOceanBiomes() { return avoidOceanBiomes; }

    // Optimization getters
    public int getBatchSize() { return batchSize; }
    public int getMaxBatchAttempts() { return maxBatchAttempts; }
    public boolean isSmartHeightScan() { return smartHeightScan; }
    public boolean isBiomePrefilter() { return biomePrefilter; }

    // CombatLogX integration getters
    public boolean isPreventCombatTeleport() { return preventCombatTeleport; }
    public String getCombatMessage() { return combatMessage; }
    public boolean isRemoveCombatPlayers() { return removeCombatPlayers; }
}