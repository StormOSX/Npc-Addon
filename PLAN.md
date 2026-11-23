# CustomNPCs Addon - Plan

Forge addon for CustomNPCs (Forge 1.20.1, loader 47+, modId `customnpcs`).
Target jar: CustomNPCs-1.20.1-GBPort-Unofficial-1.20.1.20260711.jar (unofficial fork, CC BY-NC license - keep the addon non-commercial).
Put a copy of the jar in `libs/` and depend on it with ForgeGradle `fg.deobf('local:customnpcs:1.20.1.20260711')` via a flatDir repo on `libs/` (the jar is SRG-mapped; the renamed copy `libs/customnpcs-1.20.1.20260711.jar` is what Gradle resolves). Use the Forge MDK for 1.20.1 and check current versions first.
Public API entry point: `noppes.npcs.api.NpcAPI.Instance()` (factions via `getFactions()`, NPC wrapper `ICustomNpc`, `INPCAi`, `IFaction`).

## Features

### 1. Keybind + command GUI
- Forge KeyMapping (registered via RegisterKeyMappingsEvent) opens a custom Screen.
- Client -> server packets over a SimpleChannel. Validate everything on the server.

### 2. NPC groups
- CustomNPCs has no group concept; the addon defines groups itself.
- Select NPCs by radius or a wand, tag them with a group id (store in persistent data / SavedData).

### 3. Group commands
- Formations: line, column, wedge, circle, square. Compute slot offsets relative to a leader/point, path each NPC to its slot with PathNavigation.moveTo.
- Attack: `setAttackTarget` on the group.
- Defend: set NPC home (`setHome`) to the position + `setReturnsHome(true)`.
- Patrol: generate waypoints for the NPC moving path system (may need internal classes, `EntityNPCInterface.ais`).
- Known risk: NPC AI (wander, return home, moving type) can fight commands. Switch those off while a command is active and restore them afterwards.

### 4. Factions
- Create/edit factions via `NpcAPI.Instance().getFactions()`; assign with `ICustomNpc.setFaction(id)`.
- Hostility in CustomNPCs is ONE-WAY and stored per faction (`attackFactions`): A attacks B only if A's list contains B. Mutual hostility = add to both lists.
- There is no native "allied" relation. The addon keeps its own ally list (saved with the world, e.g. SavedData):
  - Relation per faction pair: Hostile / Neutral / Allied.
  - Hostile -> add each to the other's hostile list. Neutral -> remove both. Allied -> remove both, plus record the pair in the addon's ally list.
  - Allies can be used for: no friendly fire, allied NPCs joining a fight, group commands covering allied groups.
- GUI: pick two factions, choose the relation, apply.

## Build order
1. [DONE] Project setup, mod loads alongside CustomNPCs (verified with runClient).
2. Faction relations + ally list + GUI (simplest, uses the public API).
3. Keybind + GUI screen skeleton + packets.
4. NPC groups + "move to me" command.
5. Formations.
6. Attack / defend / patrol.

Run `./gradlew build` and `runClient` after each step.

## Status / setup notes (2026-11-23)
- Step 1 done: ForgeGradle 6 (`[6.0,6.2)`), Gradle 8.8, Forge 47.4.28, JDK 17 toolchain. modId `npcaddon`, package `dev.storm.npcaddon`, main class `NpcAddon`.
- ForgeGradle 7 was abandoned: it could not remap CustomNPCs' SRG mixin refmap in dev (EntityIMixin crash).
- FG6 needs `mixin.env.remapRefMap` + `mixin.env.refMapRemappingFile` (= `build/createSrgToMcp/output.srg`) in the run config (already in build.gradle) so CustomNPCs' mixins load in runClient.
- Step 2 logic done (builds, not yet tested in runClient): `faction/Relation`, `AllyData` (SavedData), `FactionRelations`, and command `/npcaddon relation <a> <b> [hostile|neutral|allied]` (op level 2). The GUI part moves to step 3 with the screen/packets.
- Step 3 skeleton done (builds): keybind G (`client/ClientEvents`), `AddonScreen` (faction ids + relation buttons), SimpleChannel `network/Net` + `SetRelationPacket` (op-only, server validated). runClient reached main menu with addon loaded; command, screen and packet not yet exercised in-game.
- Step 3 verified in game (relation command + ally list survive reload).
- Step 4 written (builds, untested in game): `group/GroupData` (SavedData, name -> NPC UUIDs), `group/GroupMover` (walks NPCs, disables returnToStart/movingType meanwhile, restores them and moves home to the end position), commands `/npcaddon group add|remove <name> <radius>`, `delete <name>`, `list`, `come <name>`. Wand selection not done yet (radius only).
- Step 4 verified in game (add, list, come, reload). Wandering NPCs roam around their new home after `come`; accepted as intended.
- Step 5 written (builds, untested in game): `group/Formation` (line, column, wedge, circle, square) and `/npcaddon group form <name> <formation> [spacing]`, placed in front of the player facing their direction.
- Step 5 verified in game after two fixes: arrival distance 0.9 blocks + stall retries in GroupMover, and column is two abreast.
- Step 6 written (builds, untested in game): `group defend <name>` (walk to you, home moves there, return-home on), `group attack <name> <entity selector>` (setTarget), `group patrol <name> <radius>` (square of 4 waypoints via ais.setMovingPath, type 2, staggered start corner), `group stop <name>`. GroupMover gained hold mode and cancel().
- Next: test step 6; open risk is CustomNPCs AI dropping the attack target or ignoring it for non-hostile factions.
