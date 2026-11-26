# Nightshade Client

**A clean, modern PvP client for Fabric.** Beautiful menus, a drag-and-drop HUD, fight replays, 3D cosmetics, and 75+ modules, all in a single mod that works in any launcher.

**Minecraft 1.21.8 · 1.21.11 · 26.1 · 26.2 · 26.3** | Requires **Fabric API** | Sodium compatible

[Download on Modrinth](https://modrinth.com/mod/nightshadeclient) · [Matching resource pack](https://modrinth.com/resourcepack/nightshade-pvp)

---

## Features

- **Menus**: a modern main menu with your recent worlds and servers, a redesigned pause menu, and a modules menu with category tabs, search and glass module cards. Three menu styles to choose from.
- **Animated backgrounds**: Aurora Peaks, Night Forest, Deep Space and Nightshade Sky, all following your client colour.
- **HUD**: drag, scale and snap every element in the HUD Editor, with four HUD styles (Accent, Glass, Minimal, Classic) and one-click layouts (Minimal, PvP, Streamer, Survival).
- **Session Recap** and **Combo Trainer**: a summary after every fight and a full recap (kills, deaths, accuracy, crits, best combo, every fight) when you leave; live combo, best and average combo and hit trade.
- **PvP**: Hitboxes, Health Tags, Damage Indicators, Kill Effects, Hit Sounds, Hit Color, Reach Display, Shield Status, Totem Pop Counter, Custom Crosshair with a pixel Crosshair Editor, share codes and custom cooldown bars, and more.
- **Fight Replay**: keeps the last 30 seconds of the players around you; save a clip with one key and rewatch it.
- **Performance**: Entity Culling, Shadow Culling, Name Tag Culling, Entity Render Limiter, Block Entity Culling, Particle Optimizer, Dynamic FPS and more.
- **Quality of life**: Zoom, Freelook, Snaplook, Toggle Sprint, Waypoints, chat tools, screenshot manager, per-server profiles, Nick Hider, an in-game mod menu and an account switcher.
- **Minimap and World Map**: a minimap with players, mobs and waypoints, and a full-screen world map (M) where you can drop waypoints.
- **Spotify and Discord**: a Spotify HUD with album art and media keys (no login), and Discord Rich Presence.
- **Mod browser**: install mods and resource packs from Modrinth in game, with a curated Recommended list, categories, full mod pages, automatic dependencies and a warning when a mod duplicates a built-in feature.
- **Wardrobe**: 25 wings, 28 hats, 17 capes, 10 auras and 8 pets on a 3D model of yourself, a rotating Shop with daily and weekly deals, points for playing, and an emote wheel. Other Nightshade players can see your cosmetics (sign-in uses the same check Minecraft servers use; the server code is in [`cosmetics-server/`](cosmetics-server)).
- **Fonts**: Inter, Poppins or the Minecraft font, optionally applied to the whole game.

Everything is client-side. Nightshade only shows information and changes visuals; it never moves, aims or clicks for you.

## Repository layout

Each Minecraft version has its own Gradle project:

| Folder | Minecraft | Java | Build task |
|---|---|---|---|
| [`1.21.8/`](1.21.8) | 1.21.8 | 21+ | `remapJar` |
| [`1.21.11/`](1.21.11) | 1.21.11 | 21+ | `remapJar` |
| [`26.1/`](26.1) | 26.1 - 26.1.2 | 25 | `jar` |
| [`26.2/`](26.2) | 26.2 | 25 | `jar` |
| [`26.3/`](26.3) | 26.3 | 25 | `jar` |

[`cosmetics-server/`](cosmetics-server) is the online service that shares Wardrobe cosmetics between players.

This repository holds the newest source (1.32.0). Releases are published on [Modrinth](https://modrinth.com/mod/nightshadeclient).

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

The jar is written to `build/libs/`, for example `Nightshade-Client1.32.0-1.21.11.jar`. On Windows use `gradlew.bat` instead of `./gradlew`.

## Installing

1. Install **Fabric Loader** and **Fabric API** for your Minecraft version.
2. Put the Nightshade jar in your `mods` folder.
3. Launch the game and press **Right Shift** to open the modules menu.

## License

All Rights Reserved. See [LICENSE](LICENSE).

