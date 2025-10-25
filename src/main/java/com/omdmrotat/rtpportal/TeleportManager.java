package com.omdmrotat.rtpportal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

public class TeleportManager {

    private final RTPPortal plugin;

    // Caching for performance
    private final Map<String, Boolean> biomeCache = new HashMap<>();
    private final Set<Material> waterBlocksCache = new HashSet<>();
    private final Set<Biome> oceanBiomes = new HashSet<>();

    public TeleportManager(RTPPortal plugin) {
        this.plugin = plugin;
        initializeWaterBlocksCache();
        initializeOceanBiomes();
    }

    /**
     * Pre-populate water blocks cache for faster lookups
     */
    private void initializeWaterBlocksCache() {
        waterBlocksCache.add(Material.WATER);
        waterBlocksCache.add(Material.KELP);
        waterBlocksCache.add(Material.KELP_PLANT);
        waterBlocksCache.add(Material.SEAGRASS);
        waterBlocksCache.add(Material.TALL_SEAGRASS);
        waterBlocksCache.add(Material.SEA_PICKLE);
        waterBlocksCache.add(Material.SPONGE);
        waterBlocksCache.add(Material.WET_SPONGE);
        waterBlocksCache.add(Material.PRISMARINE);
        waterBlocksCache.add(Material.PRISMARINE_BRICKS);
        waterBlocksCache.add(Material.DARK_PRISMARINE);
        waterBlocksCache.add(Material.SEA_LANTERN);

        // Add all coral types
        for (Material material : Material.values()) {
            if (material.name().contains("CORAL")) {
                waterBlocksCache.add(material);
            }
        }
    }

    /**
     * Pre-populate ocean biomes set using reflection to avoid deprecated methods
     */
    private void initializeOceanBiomes() {
        // Add core ocean biomes that exist in all versions
        oceanBiomes.add(Biome.OCEAN);
        oceanBiomes.add(Biome.DEEP_OCEAN);

        // Add other ocean biomes using reflection to avoid deprecated valueOf()
        addBiomeIfExistsReflection("WARM_OCEAN");
        addBiomeIfExistsReflection("LUKEWARM_OCEAN");
        addBiomeIfExistsReflection("COLD_OCEAN");
        addBiomeIfExistsReflection("FROZEN_OCEAN");

        plugin.getLogger().info("Initialized " + oceanBiomes.size() + " ocean biomes for detection");
    }

    /**
     * Safely add a biome using reflection to avoid deprecated valueOf()
     */
    private void addBiomeIfExistsReflection(String biomeName) {
        try {
            // Use reflection to get the biome field
            java.lang.reflect.Field field = Biome.class.getDeclaredField(biomeName);
            if (field != null && field.getType() == Biome.class) {
                Biome biome = (Biome) field.get(null);
                oceanBiomes.add(biome);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Biome doesn't exist in this version, skip it silently
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking biome " + biomeName + ": " + e.getMessage());
        }
    }

    /**
     * Optimized batch location finding - tries multiple locations simultaneously
     */
    public void findAndTeleportPlayers(Set<UUID> playerUUIDs) {
        Set<UUID> playersToTeleport = new HashSet<>(playerUUIDs);
        playerUUIDs.clear();

        // Group players by their region
        Map<String, Set<UUID>> playersByRegion = new HashMap<>();
        for (UUID playerUUID : playersToTeleport) {
            String regionName = plugin.getPlayerRegion(playerUUID);
            if (regionName != null) {
                playersByRegion.computeIfAbsent(regionName, k -> new HashSet<>()).add(playerUUID);
            }
        }

        // Process each region separately
        for (Map.Entry<String, Set<UUID>> entry : playersByRegion.entrySet()) {
            String regionName = entry.getKey();
            Set<UUID> players = entry.getValue();
            
            ConfigManager.WorldRTPConfig worldConfig = plugin.getConfigManager().getWorldConfigByRegion(regionName);
            if (worldConfig == null) {
                plugin.getLogger().severe("No world config found for region: " + regionName);
                continue;
            }

            World world = Bukkit.getWorld(worldConfig.getWorldName());
            if (world == null) {
                plugin.getLogger().severe("World '" + worldConfig.getWorldName() + "' not found!");
                continue;
            }

            findLocationsBatch(world, worldConfig, players, 0);
        }
    }

    /**
     * Optimized batch location finding using parallel processing
     */
    private void findLocationsBatch(World world, ConfigManager.WorldRTPConfig worldConfig, 
                                   Set<UUID> playersToTeleport, int batchAttempt) {
        long startTime = System.currentTimeMillis();
        ConfigManager cfg = plugin.getConfigManager();
        
        // Nether-specific optimizations: reduce batch size and attempts for better performance
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        int batchSize = isNether ? Math.min(3, cfg.getBatchSize()) : cfg.getBatchSize();
        int maxBatchAttempts = isNether ? Math.min(5, cfg.getMaxBatchAttempts()) : cfg.getMaxBatchAttempts();

        if (batchAttempt >= maxBatchAttempts) {
            plugin.getLogger().severe("Could not find a safe teleport location after " + maxBatchAttempts + " batch attempts!");
            return;
        }

        // Generate multiple candidate locations using worldConfig bounds
        List<LocationCandidate> candidates = generateLocationCandidates(worldConfig, batchSize);

        // Pre-filter candidates based on biome if enabled (skip for Nether as ocean biomes don't apply)
        if (!isNether && cfg.isBiomePrefilter() && cfg.isWaterDetectionEnabled() && cfg.isAvoidOceanBiomes()) {
            candidates = candidates.stream()
                    .filter(candidate -> !isOceanBiomeCached(world, candidate.x, candidate.z))
                    .collect(Collectors.toList());
        }

        if (candidates.isEmpty()) {
            plugin.getLogger().info("All candidates filtered out by biome check, retrying...");
            findLocationsBatch(world, worldConfig, playersToTeleport, batchAttempt + 1);
            return;
        }

        // Process candidates using Folia's region scheduler for thread safety
        processCandidatesSequentially(world, worldConfig, candidates, playersToTeleport, batchAttempt, startTime, 0);
    }

    /**
     * Generate random location candidates using world-specific bounds
     */
    private List<LocationCandidate> generateLocationCandidates(ConfigManager.WorldRTPConfig worldConfig, int count) {
        List<LocationCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int x = ThreadLocalRandom.current().nextInt(worldConfig.getMinX(), worldConfig.getMaxX() + 1);
            int z = ThreadLocalRandom.current().nextInt(worldConfig.getMinZ(), worldConfig.getMaxZ() + 1);
            candidates.add(new LocationCandidate(x, z));
        }

        return candidates;
    }

    /**
     * Process candidates sequentially using region scheduler for thread safety
     */
    private void processCandidatesSequentially(World world, ConfigManager.WorldRTPConfig worldConfig,
                                             List<LocationCandidate> candidates,
                                             Set<UUID> playersToTeleport, int batchAttempt,
                                             long startTime, int candidateIndex) {
        if (candidateIndex >= candidates.size()) {
            // All candidates failed, try next batch
            plugin.getLogger().info("Batch " + (batchAttempt + 1) + " failed, trying next batch...");
            findLocationsBatch(world, worldConfig, playersToTeleport, batchAttempt + 1);
            return;
        }

        LocationCandidate candidate = candidates.get(candidateIndex);
        Location regionKeyLocation = new Location(world, candidate.x, 0, candidate.z);

        // Use region scheduler to ensure we're on the correct thread
        RegionScheduler regionScheduler = Bukkit.getRegionScheduler();
        regionScheduler.execute(plugin, regionKeyLocation, () -> {
            Location safeLocation = findSafeSpotOptimized(world, candidate.x, candidate.z);

            if (safeLocation != null) {
                // Found a safe location!
                long endTime = System.currentTimeMillis();
                ConfigManager cfg = plugin.getConfigManager();
                plugin.getLogger().info("Found safe location in " + (endTime - startTime) + "ms " +
                        "(batch " + (batchAttempt + 1) + "/" + cfg.getMaxBatchAttempts() +
                        ", candidate " + (candidateIndex + 1) + "/" + candidates.size() + ")");
                teleportPlayersToLocation(playersToTeleport, safeLocation);
            } else {
                // Try next candidate
                processCandidatesSequentially(world, worldConfig, candidates, playersToTeleport,
                                            batchAttempt, startTime, candidateIndex + 1);
            }
        });
    }

    /**
     * Simple data class for location candidates
     */
    private static class LocationCandidate {
        final int x, z;

        LocationCandidate(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * Optimized safe spot finding with smart height scanning and early exits
     */
    private Location findSafeSpotOptimized(World world, int x, int z) {
        ConfigManager cfg = plugin.getConfigManager();
        Set<Material> safeBlocks = cfg.getSafeBlockTypes();
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        boolean isEnd = world.getEnvironment() == World.Environment.THE_END;

        // Early biome check if enabled (skip for Nether/End)
        if (!isNether && !isEnd && cfg.isWaterDetectionEnabled() && cfg.isAvoidOceanBiomes()) {
            if (isOceanBiomeCached(world, x, z)) {
                return null; // Quick rejection
            }
        }

        // Smart height scanning - start from surface level instead of max height
        int startY;
        if (cfg.isSmartHeightScan()) {
            try {
                startY = world.getHighestBlockYAt(x, z) + 10;
                
                // Nether-specific: cap startY to avoid the bedrock roof (max 120)
                if (isNether) {
                    startY = Math.min(startY, 120);
                }
            } catch (Exception e) {
                // Fallback if we can't get highest block (thread safety issues)
                startY = world.getMaxHeight() - 1;
            }
        } else {
            startY = world.getMaxHeight() - 1;
        }

        int endY = Math.max(world.getMinHeight(), world.getSeaLevel() - 10);
        
        // Limit vertical scan range to reduce lag
        int effectiveStart = Math.min(startY, world.getMaxHeight() - 1);
        int minScan = Math.max(endY, effectiveStart - 64); // don't scan more than 64 blocks vertically

        // Scan downwards for safe spot
        for (int y = effectiveStart; y > minScan; y--) {
            if (isValidTeleportLocation(world, x, y, z, safeBlocks, isEnd)) {
                Location potentialLocation = new Location(world, x + 0.5, y + 1, z + 0.5);

                // Skip expensive water checks in the Nether (no water exists there)
                // and in the End to reduce work
                if (!isNether && !isEnd && cfg.isWaterDetectionEnabled()) {
                    if (!isLocationSafeFromWaterOptimized(world, x, y + 1, z)) {
                        continue;
                    }
                }

                return potentialLocation;
            }
        }

        return null;
    }

    /**
     * Optimized validation for teleport location
     */
    private boolean isValidTeleportLocation(World world, int x, int y, int z, Set<Material> safeBlocks, boolean isEnd) {
        // Check ground block
        Material groundMaterial = world.getBlockAt(x, y, z).getType();
        
        // Avoid landing on bedrock (Nether roof or other bedrock formations)
        if (groundMaterial == Material.BEDROCK) {
            return false;
        }
        
        // End-specific check: prefer end stone as ground to avoid void/air columns
        if (isEnd && groundMaterial != Material.END_STONE) {
            return false;
        }
        
        if (!safeBlocks.contains(groundMaterial)) {
            return false;
        }

        // Check player space (feet and head)
        Material feetMaterial = world.getBlockAt(x, y + 1, z).getType();
        Material headMaterial = world.getBlockAt(x, y + 2, z).getType();

        // Reject if feet or head are solid
        if (feetMaterial.isSolid() || headMaterial.isSolid()) {
            return false;
        }
        
        // Reject lava at feet (especially important for Nether)
        if (feetMaterial == Material.LAVA) {
            return false;
        }

        return true;
    }

    /**
     * Optimized water safety check with early exits and reduced block access
     */
    private boolean isLocationSafeFromWaterOptimized(World world, int x, int y, int z) {
        ConfigManager config = plugin.getConfigManager();

        // Quick overhead check first (most likely to fail)
        if (config.isCheckWaterAbove()) {
            int checkHeight = config.getCheckAboveHeight();
            for (int dy = 1; dy <= checkHeight; dy++) {
                Material material = world.getBlockAt(x, y + dy, z).getType();
                if (isWaterBlockCached(material) || material == Material.LAVA) {
                    return false; // Early exit
                }
            }
        }

        // Area percentage check
        int radius = config.getWaterCheckRadius();
        double maxWaterPercentage = config.getMaxWaterPercentage();

        int totalBlocks = 0;
        int liquidBlocks = 0;

        // Optimized scanning with early exit when threshold exceeded
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Material material = world.getBlockAt(x + dx, y + dy, z + dz).getType();
                    totalBlocks++;

                    if (isWaterBlockCached(material) || material == Material.LAVA) {
                        liquidBlocks++;

                        // Early exit if we already exceed threshold
                        double currentPercentage = (double) liquidBlocks / totalBlocks;
                        if (currentPercentage > maxWaterPercentage && totalBlocks > 10) {
                            return false;
                        }
                    }
                }
            }
        }

        // Final percentage check
        double liquidPercentage = (double) liquidBlocks / totalBlocks;
        return liquidPercentage <= maxWaterPercentage;
    }

    /**
     * Optimized water block check using pre-populated cache
     */
    private boolean isWaterBlockCached(Material material) {
        return waterBlocksCache.contains(material);
    }

    /**
     * Cached biome checking for better performance using direct biome comparison
     */
    private boolean isOceanBiomeCached(World world, int x, int z) {
        String cacheKey = x + "," + z;

        return biomeCache.computeIfAbsent(cacheKey, key -> {
            try {
                Biome biome = world.getBiome(x, 64, z);
                return oceanBiomes.contains(biome);
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Clean up caches periodically to prevent memory leaks
     */
    public void cleanupCaches() {
        if (biomeCache.size() > 1000) {
            biomeCache.clear();
            plugin.getLogger().info("Cleared biome cache (size was " + biomeCache.size() + ")");
        }
    }

    private void teleportPlayersToLocation(Set<UUID> players, Location location) {
        /* donutsmp rtpzone title replica */
        final Title successTitle = Title.title(
                Component.text("ʀᴛᴘ ᴢᴏɴᴇ", NamedTextColor.RED),
                Component.text("Fight players and steal their loot", NamedTextColor.WHITE),
                Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))
        );

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Double-check combat status before teleporting (safety measure)
                if (plugin.getConfigManager().isPreventCombatTeleport() &&
                    plugin.getCombatLogXIntegration().isInCombat(player)) {
                    plugin.getLogger().info("Skipping teleportation for " + player.getName() + " - still in combat.");
                    continue;
                }

                player.teleportAsync(location).thenAccept(success -> {
                    if (success) {
                        player.showTitle(successTitle);
                    }
                });
            }
        }
    }
}