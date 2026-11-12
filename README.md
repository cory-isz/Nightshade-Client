# Nightshade Client

**A clean, modern PvP client for Fabric.** Beautiful menus, a drag-and-drop HUD, fight replays, client-side cosmetics, and 75+ modules, all in a single mod that works in any launcher.

**Minecraft 1.21.11 · 26.1 · 26.2 · 26.3** | Requires **Fabric API** | Sodium compatible

[Download on Modrinth](https://modrinth.com/mod/nightshadeclient) · [Matching resource pack](https://modrinth.com/resourcepack/nightshade-pvp)

---

## Features

- **Menus**: a modern main menu with your recent worlds and servers, a redesigned pause menu, and a modules menu with category tabs, search and glass module cards. Three menu styles to choose from.
- **Animated backgrounds**: Aurora Peaks, Night Forest, Deep Space and Nightshade Sky, all following your client colour.
- **HUD**: drag, scale and snap every element in the HUD Editor, with four HUD styles (Accent, Glass, Minimal, Classic) and one-click layouts (Minimal, PvP, Streamer, Survival).
- **PvP**: Hitboxes, Health Tags, Damage Indicators, Kill Effects, Hit Sounds, Hit Color, Reach Display, Shield Status, Totem Pop Counter, Custom Crosshair with a pixel Crosshair Editor, share codes and custom cooldown bars, and more.
- **Fight Replay**: keeps the last 30 seconds of the players around you; save a clip with one key and rewatch it.
- **Performance**: Entity Culling, Shadow Culling, Name Tag Culling, Entity Render Limiter, Block Entity Culling, Particle Optimizer, Dynamic FPS and more.
- **Quality of life**: Zoom, Freelook, Snaplook, Toggle Sprint, Waypoints, chat tools, screenshot manager, per-server profiles, Nick Hider, an in-game mod menu and an account switcher.
- **Minimap and World Map**: a minimap with players, mobs and waypoints, and a full-screen world map (M) where you can drop waypoints.
- **Spotify and Discord**: a Spotify HUD with album art and media keys (no login), and Discord Rich Presence.
- **Mod browser**: install mods and resource packs from Modrinth in game, with a curated Recommended list, categories, full mod pages, automatic dependencies and a warning when a mod duplicates a built-in feature.
- **Cosmetics**: capes and elytra skins that are fully client-side; only you see them.
- **Fonts**: Inter, Poppins or the Minecraft font, optionally applied to the whole game.

Everything is client-side. Nightshade only shows information and changes visuals; it never moves, aims or clicks for you.

## Repository layout

Each Minecraft version has its own Gradle project:

| Folder | Minecraft | Java | Build task |
|---|---|---|---|
| [`1.21.11/`](1.21.11) | 1.21.11 | 21+ | `remapJar` |
| [`26.1/`](26.1) | 26.1 - 26.1.2 | 25 | `jar` |
| [`26.2/`](26.2) | 26.2 | 25 | `jar` |
| [`26.3/`](26.3) | 26.3 | 25 | `jar` |

## Building

Install a JDK (25 works for every folder), then run the build task in the folder for your Minecraft version:

```bash
cd 1.21.11
./gradlew remapJar
```

```bash
cd 26.3
./gradlew jar
```

The jar is written to `build/libs/`, for example `Nightshade-Client1.24.0-1.21.11.jar`. On Windows use `gradlew.bat` instead of `./gradlew`.

## Installing

1. Install **Fabric Loader** and **Fabric API** for your Minecraft version.
2. Put the Nightshade jar in your `mods` folder.
3. Launch the game and press **Right Shift** to open the modules menu.

## License

All Rights Reserved. See [LICENSE](LICENSE).

---

*Made and developed by Itz_cory.*
