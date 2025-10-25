# RTPPortal - AI Coding Agent Instructions

## Project Overview
Minecraft plugin (Paper/Folia) that creates region-based random teleportation portals with multi-world support, dimension-specific optimizations, and safety features. Players enter WorldGuard regions to trigger RTP with countdown timers.

**Tech Stack**: Java 21, Paper API 1.21+, Maven, WorldGuard (required), PlaceholderAPI (optional), CombatLogX (optional)

## Architecture & Core Components

### Plugin Lifecycle (`RTPPortal.java`)
- **Folia Detection**: Auto-detects Folia vs Paper/Bukkit at startup via reflection (`Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`)
- **Scheduling Model**: Uses Paper's `AsyncScheduler` for Folia compatibility (not Bukkit scheduler)
- **State Management**: Thread-safe collections (`Collections.synchronizedSet/Map`) for player tracking
  - `playersInPortal`: Set of player UUIDs currently in any portal region
  - `playerRegionMap`: Maps UUID → region name to support multi-world portals

### Multi-World Configuration (`ConfigManager.java`)
Config structure changed from single-world to multi-world in recent update:
```yaml
worlds:
  overworld:
    region-name: "portal_rtp"
    teleport-bounds:
      world: "world"
      x: {min: -1000, max: 1000}
      z: {min: -1000, max: 1000}
```
- Each world has its own WorldGuard region and teleport boundaries
- `regionToWorld` map enables quick lookups: region name → world key
- All portals share the same countdown timer (global setting)

### Dimension-Specific Teleportation (`TeleportManager.java`)
**Critical Pattern**: Different optimization strategies per dimension (inspired by DonutPlusRTP)

#### Nether Optimizations
- **Reduced batch processing**: Max 3 candidates (vs 5 for Overworld) to reduce lag
- **Height capping**: Y-coordinate capped at 120 to avoid bedrock roof at Y=128
- **Skip water checks**: Water can't exist in Nether (evaporates), saves ~40% block scanning
- **Skip ocean biome filtering**: No ocean biomes in Nether/End
- **Bedrock rejection**: Prevents landing on bedrock (floor Y=0 or ceiling Y=128)

#### End Optimizations
- **End Stone requirement**: Players can ONLY land on `Material.END_STONE` to prevent void teleports
- **Skip water/ocean checks**: Like Nether, no water exists in End

#### Performance Pattern
```java
boolean isNether = world.getEnvironment() == World.Environment.NETHER;
boolean isEnd = world.getEnvironment() == World.Environment.THE_END;
// Adjust batch size and max attempts based on dimension
```

### Region Detection (`WorldGuardRegionListener.java`)
- **Movement optimization**: Only processes events when block coordinates change (not sub-block movement)
- **Multi-region support**: Checks all configured regions via `getAllRegionNames()`
- **Single-region constraint**: Players can only be in ONE portal region at a time
- **Entry/Exit pattern**: Uses `wasInRegion` vs `isInRegion` to detect transitions

### Countdown & Teleportation (`PortalRTPTask.java`)
- Runs async at 1-second fixed rate via `AsyncScheduler`
- Decrements global `AtomicInteger` timer
- On timer expiration: calls `TeleportManager.findAndTeleportPlayers()`
- Groups players by their tracked region for batch processing

## Critical Development Patterns

### Folia Threading Rules (READ THIS CAREFULLY)
This plugin uses Paper's **region-based schedulers** for Folia compatibility:
1. **DO NOT** use `Bukkit.getScheduler()` - will break on Folia
2. **DO** use `getServer().getAsyncScheduler()` for background tasks
3. **DO** use `getServer().getRegionScheduler()` for location-specific tasks (blocks/chunks)
4. **DO** use `getServer().getEntityScheduler()` for player/entity operations

**Example Pattern** (see `TeleportManager.java`):
```java
// WRONG - synchronous teleport breaks Folia
player.teleport(location);

// RIGHT - region-aware teleport
RegionScheduler scheduler = plugin.getServer().getRegionScheduler();
scheduler.run(plugin, location, task -> {
    player.teleport(location);
});
```

### Safety Validation Chain
Location validation follows this order (see `isValidTeleportLocation()` and `findSafeSpotOptimized()`):
1. Bedrock rejection (all dimensions)
2. Lava at feet rejection (critical for Nether)
3. End Stone requirement (End dimension only)
4. Safe block type validation (dimension-specific list in config)
5. Water/ocean checks (skipped for Nether/End)
6. Vertical scan limiting (max 64 blocks to prevent lag)

### Cache Management
`TeleportManager` maintains performance caches:
- `biomeCache`: String key → Boolean (ocean biome check results)
- `waterBlocksCache`: Set of water-related materials
- `oceanBiomes`: Set of ocean biomes (initialized via reflection to avoid deprecation)
- **Auto-cleanup**: Caches cleared every 5 minutes via async scheduler

### Combat Integration (`CombatLogXIntegration.java`)
- Optional dependency: only activates if CombatLogX detected
- Config option `prevent-combat-teleport`: blocks RTP for players in combat
- Config option `remove-combat-players`: whether to eject combat players from portal

## Build & Testing

### Maven Build
```bash
mvn clean package
```
Output: `target/rtpportal-1.0-SNAPSHOT.jar`

### Gradle Build (DonutPlusRTP subproject)
```bash
cd DonutPlusRTP
./gradlew build shadowJar
```

### Testing Checklist
When modifying teleportation logic, test ALL dimensions:
- **Overworld**: Near oceans, various biomes, caves
- **Nether**: Near lava lakes, bedrock ceiling (Y>120), various Nether biomes
- **End**: Main island, outer islands, near void edges
- **Performance**: Monitor TPS during mass teleportations

## Common Tasks

### Adding New Safe Block Types
Edit `config.yml` → `safe-block-types` list. Blocks are dimension-agnostic in config but validated per-dimension in code.

### Adjusting Dimension-Specific Behavior
Modify `TeleportManager.findLocationsBatch()` and `findSafeSpotOptimized()`. Look for `isNether` and `isEnd` boolean flags.

### Adding New World Portal
Add entry to `config.yml` under `worlds:`. Region name must be unique across all worlds.

### Debugging Threading Issues
Check Folia detection: `RTPPortal.isFolia()` static method. If true, ensure all Bukkit scheduler calls are replaced with region/entity/async schedulers.

## Key Files Reference
- `RTPPortal.java`: Plugin main, scheduler initialization, Folia detection
- `TeleportManager.java`: Core teleportation logic, dimension-specific optimizations (~450 lines)
- `ConfigManager.java`: Multi-world config parsing, WorldRTPConfig inner class
- `WorldGuardRegionListener.java`: Region entry/exit detection, movement optimization
- `PortalRTPTask.java`: Countdown timer, triggers batch teleportation


## Conventions & Style
- Use **Adventure Component API** for all messages (not legacy Chat API)
- Use **reflection** to handle biome enum changes across Minecraft versions (see `addBiomeIfExistsReflection()`)
- **No deprecated APIs**: Avoid `Biome.valueOf()`, use reflection instead
- **Thread-safe collections**: Always use `Collections.synchronized*` for shared state
- **Atomic operations**: Use `AtomicInteger` for counters accessed across threads
