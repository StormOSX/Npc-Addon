# NPC Command Addon

A Forge addon for [CustomNPCs](https://www.curseforge.com/minecraft/mc-mods/customnpcs) (Minecraft 1.20.1). It adds squads you can command, formations, richer faction relations, and a GUI to drive it all.

## Features

- **Squads:** group NPCs by radius and give them orders: follow, defend, attack, patrol, stop.
- **Monster hostility:** squad NPCs attack enemies (hostile mobs, hostile factions) they can see within their aggro range, always turning to the closest one. Explicit attack orders stay on their target. Hold fire and passive squads do not join in.
- **Area attack:** order a squad to clear an area: it walks there, kills every hostile monster and hostile-faction NPC inside the radius, then holds the spot and engages newcomers.
- **Retreat and rally:** a squad member whose health drops to the squad's threshold (10% by default; off, 10, 25 or 50% in the Squads tab, any value up to 90 by command) breaks off and runs from nearby enemies. Once it is safe and back to half health (or 25 points above a high threshold) it resumes what it was doing. The Rally button calls retreating members back to you at once, and any other order also ends the retreat.
- **Medics:** click a member in the Squads tab (or `group medic`) to make it a medic, marked with `+`. Medics never fight. Every 2 seconds they heal the most wounded squad member within 10 blocks (themselves included) by a tenth of its maximum health, walk to badly wounded members within 24 blocks, back away from nearby enemies and take the rear in formations. Orders that walk the squad somewhere come first.
- **Mounted squads:** the Mount order (or `group mount`) sends members to free horses, donkeys, mules, skeleton and zombie horses near you. Right-click a horse, or a CustomNPC that is not in a squad and not hostile, with the wand to send the nearest member to ride it; the Mount order also uses NPCs that have been ridden before. No saddle or taming is needed. Riders obey the usual orders from the saddle until Dismount.
- **Recruiting:** an operator saves NPCs to the recruit list, in the Recruit tab or with `/npcaddon recruit save <type> <name>` while looking at one. Players hire copies in the Recruit tab for gold ingots (melee 2, ranged 5, mounted 7, engineer 8), at most one every 30 seconds. The recruit appears some way off, walks to them (mounted recruits ride in on their own horse), joins a squad of theirs and follows; they receive a command wand. Only mounted recruits (and ordinary NPCs) ride mounts, only engineers (and ordinary NPCs) crew siege weapons. Sneak + right-click your own recruit to dismiss it (a hired copy then walks or rides away and vanishes); a recruit that dies leaves the squad and does not respawn.
- **Squad HUD:** a panel with the selected squad's members (health, current order), a marker above each member and arrows on the screen edge, with distance, for members out of view. Toggle with `H` (rebindable). It follows the squad selected with the wand or in the Squads tab.
- **Stances:** per squad: aggressive (wider aggro range), defensive (the NPCs' own settings), hold fire (only fights whoever hurt it) and passive (never fights). Attack orders always override them.
- **Command wand:** an item (creative tab, `/give @s npcaddon:command_wand`) to command squads from a distance. Left-click an NPC selects its squad, right-click the ground moves the squad there and holds, right-click an entity attacks it, Ctrl + right-click attacks the area around the target, sneak + right-click toggles follow/stop.
- **Facing:** formations, defend and follow can face a compass direction (0 = north, 90 = east, or n/ne/e/...). Without one, squads face the way you face; a circle always faces outwards.
- **Formations:** line, column, wedge, circle and square, with adjustable spacing. In mixed squads, melee NPCs take the front and ranged NPCs (those with a projectile set) the back: a second rank in a line, an inner ring in a circle. Move, defend and area orders (including the wand) use the formation the squad last formed up in, and place them the same way.
- **Ranged keep their distance:** a ranged squad NPC backs away while shooting when its target comes within 8 blocks (less for short-ranged NPCs). It never steps off a ledge, and fights hand to hand inside its own melee range setting.
- **Chat replies:** the nearest squad member answers each order ("Defending you", "Attacking target", ...).
- **Faction relations:** set two factions hostile, neutral or allied (CustomNPCs only has one-way hostility; the addon adds a saved ally list).
- **Ancient Warfare 2 (optional):** if AW is installed, its factions appear in the Factions and Players tabs. A CustomNPCs faction and an AW faction can be set hostile, neutral or allied (always mutual; allied factions never target each other, hostile ones hunt each other). A player's standing with an AW faction can be set from the Players tab; AW's own faction-vs-faction relations are left alone.
- **Siege weapons (needs Ancient Warfare):** a squad order makes free members crew nearby catapults, trebuchets, ballistae, cannons and hwachas and battering rams. Ranged crews pick hostile targets on aggressive/defensive stance, shoot an ordered target, or bombard a spot you choose; they hold automatic fire when a friend is near the target. Crews of mobile weapons drive them: move, defend and formation orders send them to a row behind the squad, follow trails you, stop halts them and Dismount gets them out (fixed weapons are left behind by movement orders). A ram crew attacks only what you order: right-click an Ancient Warfare gate or an enemy with the wand and it drives there and rams it. Area and patrol orders leave crews where they are. Wand: right-click a weapon to crew it, Alt + right-click to bombard (last used radius). While Ctrl or Alt is held, a ring shows where the area attack (red) or bombardment (orange) will land. Squads tab: Man and Bombard buttons. Ammo is unlimited by default (`siege.unlimitedAmmo` in `config/npcaddon-common.toml`).
- **Player standing:** make a faction hostile, neutral or friendly to a player, or set the starting standing for new players.
- **GUI:** press `G` for the Squads, Factions and Players screens. Everything is also available as `/npcaddon` commands.

Squad orders (commands, wand and Squads tab) work for every player on their own squads; operators (level 2) can command all squads. Creating squads by radius, recruit types, and faction and player settings need operator permission.

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
/npcaddon group retreat <name> [percent 0..90]      0 = never retreat, default 10
/npcaddon group medic <name> <npc>                  make a member a medic, or not
/npcaddon group rally <name>                        retreating members come back to you
/npcaddon group attackarea <name> [radius] [x y z]   default radius 10, centred on you
/npcaddon group moveto <name> <x y z> [facing]
/npcaddon group attack <name> <target>        e.g. @e[type=minecraft:zombie,sort=nearest,limit=1]
/npcaddon group patrol <name> <radius>
/npcaddon group form <name> <line|column|wedge|circle|square> [spacing] [facing]
/npcaddon relation <factionA> <factionB> [hostile|neutral|allied]
/npcaddon awrelation <faction id> <aw faction> [hostile|neutral|allied]   needs Ancient Warfare
/npcaddon awplayer <players> <aw faction> <hostile|neutral|friendly>   needs Ancient Warfare
/npcaddon awfactions
/npcaddon recruit save <melee|ranged|mounted|engineer> <name>   the NPC you look at
/npcaddon recruit delete <name>
/npcaddon recruit list
/npcaddon group mount <name> [radius]                mount free horses and mount NPCs near you
/npcaddon group mountone <name> <entity>
/npcaddon group dismount <name>                      riders and siege crews get off
/npcaddon group manweapons <name> [radius]            crew free siege weapons near you (Ancient Warfare)
/npcaddon group manweapon <name> <weapon entity>
/npcaddon group bombard <name> <x y z> [radius 0..32]
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
