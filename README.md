# RTPPortal

A powerful Minecraft plugin that creates region-based random teleportation portals with advanced safety features and Folia support.

## Features

### Core Functionality

- **Region-Based Portals**: Uses WorldGuard regions as teleportation portals.
- **Countdown Timer**: Configurable teleportation delay with a visual countdown.
- **Safe Teleportation**: Advanced location validation to ensure player safety.
- **Folia Support**: Fully compatible with Folia server software.
- **Combat Integration**: Integrates with CombatLogX to prevent combat logging.

### Safety Features

- **Liquid Detection**: Prevents teleporting into hazardous water/lava areas.
- **Safe Block Validation**: Only teleports to safe block types.
- **Biome Filtering**: Can avoid dangerous biomes like oceans.
- **Height Scanning**: Smart detection of the surface level.
- **Performance Optimization**: Batch processing and caching for better performance.

### Integrations

- **WorldGuard**: Required for region-based portals.
- **PlaceholderAPI**: Placeholder for the portals timer.
- **CombatLogX**: Optional feature for combat prevention.

## Requirements

- **Minecraft Version**: 1.21+
- **Server Software**: Paper/Purpur (with Folia support)
- **Java Version**: 21+
- **Required Dependencies**: WorldGuard
- **Optional Dependencies**: PlaceholderAPI, CombatLogX

## Installation

1.  Download the latest version from the releases page.
2.  Place the `rtpportal-1.0-SNAPSHOT.jar` file into your server's `plugins` folder.
3.  Ensure WorldGuard is installed and running.
4.  Start/restart your server.
5.  Configure the plugin using the generated `config.yml` file.

## Configuration

### Basic Setup

1.  **Create a WorldGuard Region**:

    ```
    //wand
    //pos1 (select the first corner)
    //pos2 (select the second corner)
    /rg define portal_rtp
    ```

2.  **Plugin Configuration**: Edit `plugins/rtpportal/config.yml`

### Configuration Options

```yaml
# The name of the WorldGuard region that acts as the portal
region-name: "portal_rtp"

# Teleportation countdown timer (seconds)
teleport-timer: 30

# Random teleportation boundaries
teleport-bounds:
  world: "world"
  x:
    min: -1000
    max: 1000
  z:
    min: -1000
    max: 1000

# Safe block types to land on
safe-block-types:
  - STONE
  - DIRT
  - GRASS_BLOCK
  - SAND

# Water/liquid detection settings
water-detection:
  enabled: true
  check-radius: 3
  max-water-percentage: 0.3
  check-above: true
  check-above-height: 5
  avoid-ocean-biomes: true

# CombatLogX Integration
combatlogx:
  prevent-combat-teleport: true
  combat-message: "&cYou cannot be teleported while in combat!"
  remove-combat-players: false

# Performance Optimization
optimization:
  batch-size: 5
  max-batch-attempts: 10
  smart-height-scan: true
  biome-prefilter: true
  sequential-processing: true
```

## Usage

### For Players

1.  **Enter the Portal**: Step into the designated WorldGuard region.
2.  **Wait for the Countdown**: Remain in the region for the duration of the countdown.
3.  **Teleport**: You will be randomly teleported to a safe location.

### For Admins

- **Region Setup**: Create WorldGuard regions where players can trigger the RTP.
- **Configuration**: Customize teleportation boundaries, safety checks, and timers.
- **Monitoring**: Use PlaceholderAPI placeholders to display portal status.

## Commands

This plugin is primarily region-based and does not require commands for normal operation. All interactions occur through WorldGuard regions.

## Permissions

The plugin currently operates without specific permissions - all players can use the portal regions by default. You can use WorldGuard's region flags to control access:

```yaml
# Example of WorldGuard region flags
/rg flag portal_rtp entry allow
/rg flag portal_rtp entry-deny-message "You do not have permission to use this portal!"
```

## PlaceholderAPI Support

When PlaceholderAPI is installed, the following placeholders are available:

- `%rtpportal_timer%` - The current countdown timer.
- `%rtpportal_in_portal%` - Whether the player is currently inside the portal region.

## Performance Considerations

### For Large Servers

- Adjust the `batch-size` to balance performance and CPU usage.
- Enable `sequential-processing` for Folia servers.
- Use `biome-prefilter` to minimize unnecessary checks.

### Memory Usage

- The plugin includes automatic cache clearing every 5 minutes.
- Biome and block type caches improve performance over time.

## Troubleshooting

### Common Issues

**Portal not working:**

- Verify that the WorldGuard region name matches the `region-name` in the configuration.
- Check if WorldGuard is installed and loaded correctly.
- Ensure that the region exists in the correct world.

**Players not being teleported:**

- Check if CombatLogX is preventing the teleport.
- Verify that safe block types are available within the teleportation boundaries.
- Increase `max-batch-attempts` if locations are hard to find.

**Performance issues:**

- Reduce the `batch-size` to lower CPU usage.
- Enable `sequential-processing` on Folia.
- Adjust the teleportation boundaries to avoid problematic areas.

## Development

### Building from Source

```bash
git clone <repository-url>
cd rtpportal
mvn clean package
```

The compiled JAR file will be in the `target/` directory.

### Project Structure

- `src/main/java/com/omdmrotat/rtpportal/` - The main plugin source code.
- `src/main/resources/` - Configuration files and plugin.yml.
- `pom.xml` - Maven configuration with dependencies.

## Contributing

1.  Fork the repository.
2.  Create a feature branch.
3.  Make your changes.
4.  Test thoroughly.
5.  Submit a pull request.

## License

This project is licensed under the GPLv3 license - see the LICENSE file for details.

## Support

For support, bug reports, or feature requests:

- Create an issue on the GitHub repository.

## Changelog

### Version 1.0-SNAPSHOT

- Initial release
- Region-based RTP portals