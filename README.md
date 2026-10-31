# SmoothMiner

A client-side Fabric mining helper for Minecraft 26.2. No Paper plugin or server-side mod is included.

## Features
- Toggle with **H** by default; rebind under Options → Controls.
- Searches for a configured block in a small radius.
- Gradual camera rotation instead of instant snapping.
- Holds Minecraft's normal attack input only while the selected target is under the crosshair.
- No stealth or anti-cheat bypass features.

## Configuration
After the first launch, edit `config/smoothminer.json`:

```json
{
  "targetBlock": "minecraft:diamond_ore",
  "searchRadius": 4,
  "turnDegreesPerTick": 4.0
}
```

- `targetBlock`: block registry ID, e.g. `minecraft:iron_ore`.
- `searchRadius`: scan radius, clamped to 1–8 blocks.
- `turnDegreesPerTick`: maximum camera turn per tick, clamped to 0.5–15 degrees.

Restart Minecraft after editing the file.

## Build
Requires Java 25 and a compatible Gradle installation.

```bash
gradle build
```

The JAR should be generated in `build/libs/`.

## Compatibility status
This is an initial source draft. It has **not yet been compiled or runtime-tested** against Minecraft 26.2. Fabric/Minecraft API names and dependency versions may need adjustment before it builds. Multiplayer behavior also depends on the server's rules, reach checks, protections, and anti-cheat configuration. Use only where automation is permitted.
