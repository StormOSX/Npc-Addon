# NPC Command Addon

A Forge addon for [CustomNPCs](https://www.curseforge.com/minecraft/mc-mods/customnpcs) (Minecraft 1.20.1). It adds squads you can command, formations, richer faction relations, and a GUI to drive it all.

## Features

- **Squads:** group NPCs by radius and give them orders: come, follow, defend, attack, patrol, stop.
- **Formations:** line, column, wedge, circle and square, with adjustable spacing.
- **Chat replies:** the nearest squad member answers each order ("Defending you", "Attacking target", ...).
- **Faction relations:** set two factions hostile, neutral or allied (CustomNPCs only has one-way hostility; the addon adds a saved ally list).
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
/npcaddon group come|follow|defend|stop <name>
/npcaddon group attack <name> <target>        e.g. @e[type=minecraft:zombie,sort=nearest,limit=1]
/npcaddon group patrol <name> <radius>
/npcaddon group form <name> <line|column|wedge|circle|square> [spacing]
/npcaddon relation <factionA> <factionB> [hostile|neutral|allied]
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
