# SmoothMiner

A client-side Fabric mining helper for Minecraft 26.2. No Paper plugin or server-side mod is included.

## Features
- Toggle with **H** by default; rebind under Options → Controls.
- Searches for a configured block in a small radius.
- Eases the camera towards the ore (soft start, soft stop, no overshoot) instead of snapping.
- Stops turning as soon as the crosshair is on the target block, so there is no jitter while mining.
- Holds Minecraft's normal attack input only while the selected target is under the crosshair.
- No stealth or anti-cheat bypass features.

## Settings
Open **Mod Menu → SmoothMiner → Configure**. Changes apply as soon as you press **Done**, with no restart.
Every option has an explanation under it and a tooltip showing its default and range.

| Option | Default | What it does |
| --- | --- | --- |
| Target block | `minecraft:diamond_ore` | The one block type to mine, as its ID. Deepslate ores are separate blocks (`minecraft:deepslate_diamond_ore`). Hold F3+H in game to see IDs in item tooltips. The screen shows the block's name in green, or a red error if the ID doesn't exist. |
| Search radius | 4 | How far around you (1–8 blocks, as a cube) to look. Normal reach is only about 4.5 blocks, so larger values make it turn towards ore it can't mine yet. |
| Max turn speed | 60°/s | The fastest the camera may rotate (10–360°/s). Lower is calmer. |
| Smoothness | 7 | How gently the camera speeds up and slows down (1–10). Raise it if turning still looks jerky. |

Mod Menu is optional. Without it you can edit `config/smoothminer.json` by hand and restart the game:

```json
{
  "targetBlock": "minecraft:diamond_ore",
  "searchRadius": 4,
  "maxTurnSpeed": 60.0,
  "smoothness": 7
}
```

> The old `turnDegreesPerTick` option was replaced by `maxTurnSpeed` and `smoothness`. An old config file still loads; the new options just start at their defaults.

## Build
Requires Java 25.

```bash
./gradlew build
```

The JAR is generated in `build/libs/` (use `smoothminer-<version>.jar`, not the `-sources` one). Every push to `main` is also built by GitHub Actions; download the JAR from the run's **Artifacts** section.

## Compatibility status
Compiles against Minecraft 26.2 with Fabric Loader 0.19.5, Fabric API 0.161.0+26.2 and Mod Menu 20.0.1. Multiplayer behavior depends on the server's rules, reach checks, protections, and anti-cheat configuration. Use only where automation is permitted.
