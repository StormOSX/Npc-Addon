# NPC Command Addon

A Forge addon for [CustomNPCs](https://www.curseforge.com/minecraft/mc-mods/customnpcs) (Minecraft 1.20.1). It adds squads you can command, formations, richer faction relations, and a GUI to drive it all.

## Features

- **Squads:** group NPCs by radius and give them orders: follow, defend, attack, patrol, stop.
- **Monster hostility:** squad NPCs attack hostile vanilla mobs (zombies, skeletons, creepers...) they can see within their aggro range. Hold fire and passive squads do not.
- **Area attack:** order a squad to clear an area: it walks there, kills every hostile monster and hostile-faction NPC inside the radius, then holds the spot and engages newcomers.
- **Squad HUD:** a panel with the selected squad's members (health, current order), a marker above each member and arrows on the screen edge, with distance, for members out of view. Toggle with `H` (rebindable). It follows the squad selected with the wand or in the Squads tab.
- **Stances:** per squad: aggressive (wider aggro range), defensive (the NPCs' own settings), hold fire (only fights whoever hurt it) and passive (never fights). Attack orders always override them.
- **Command wand:** an item (creative tab, `/give @s npcaddon:command_wand`) to command squads from a distance. Left-click an NPC selects its squad, right-click the ground moves the squad there and holds, right-click an entity attacks it, Ctrl + right-click attacks the area around the target, sneak + right-click toggles follow/stop.
- **Facing:** formations, defend and follow can face a compass direction (0 = north, 90 = east, or n/ne/e/...). Without one, squads face the way you face; a circle always faces outwards.
- **Formations:** line, column, wedge, circle and square, with adjustable spacing.
- **Chat replies:** the nearest squad member answers each order ("Defending you", "Attacking target", ...).
- **Faction relations:** set two factions hostile, neutral or allied (CustomNPCs only has one-way hostility; the addon adds a saved ally list).
- **Ancient Warfare 2 (optional):** if AW is installed, its factions appear in the Factions and Players tabs. A CustomNPCs faction and an AW faction can be set hostile, neutral or allied (always mutual; allied factions never target each other, hostile ones hunt each other). A player's standing with an AW faction can be set from the Players tab; AW's own faction-vs-faction relations are left alone.
- **Player standing:** make a faction hostile, neutral or friendly to a player, or set the starting standing for new players.
- **GUI:** press `G` for the Squads, Factions and Players screens. Everything is also available as `/npcaddon` commands.

All addon commands and screens require operator permission (level 2).

## Requirements

- Minecraft 1.20.1 with Forge 47.x
- CustomNPCs for 1.20.1. Built and tested against the unofficial `CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711` build; other builds may work but are untested.
- The addon must be installed on both the client and the server.

## Commands

```
/npcaddon group add|remove <name> <radius>
/npcaddon group delete <name>
/npcaddon group list
/npcaddon group follow|defend <name> [facing]
/npcaddon group stop <name>
/npcaddon group stance <name> [aggressive|defensive|hold|passive]
/npcaddon group attackarea <name> [radius] [x y z]   default radius 10, centred on you
/npcaddon group moveto <name> <x y z> [facing]
/npcaddon group attack <name> <target>        e.g. @e[type=minecraft:zombie,sort=nearest,limit=1]
/npcaddon group patrol <name> <radius>
/npcaddon group form <name> <line|column|wedge|circle|square> [spacing] [facing]
/npcaddon relation <factionA> <factionB> [hostile|neutral|allied]
/npcaddon awrelation <faction id> <aw faction> [hostile|neutral|allied]   needs Ancient Warfare
/npcaddon awplayer <players> <aw faction> <hostile|neutral|friendly>   needs Ancient Warfare
/npcaddon awfactions
/npcaddon player <players> <faction> <hostile|neutral|friendly>
/npcaddon default <faction> <hostile|neutral|friendly> [apply]
```

Tip: use `sort=nearest` in target selectors, otherwise `limit=1` may pick a distant entity.

## Building

1. Put the CustomNPCs jar in `libs/` (it is not included in this repository). The build expects the SRG-mapped jar renamed to `customnpcs-1.20.1.20260711.jar`; see `build.gradle` and `PLAN.md`.
2. Run `./gradlew build` (add `--offline` if you have no network). The jar is written to `build/libs/`.
3. `./gradlew runClient` starts a dev client.

The project uses ForgeGradle 6 and a JDK 17 toolchain. ForgeGradle 7 cannot remap CustomNPCs' mixin refmap in dev, so do not upgrade it.

## License

Declared as CC BY-NC 4.0 in the mod metadata, matching CustomNPCs' non-commercial terms. CustomNPCs itself is not part of this repository and remains under its own license.
