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

## Status (2026-11-23, later)
- All six build-order steps are done and verified in game.
- Added after the plan: chat replies from squads, follow order, attack that widens aggro range, tabbed GUI (Squads / Factions / Players), per-player faction standing (`/npcaddon player`) and faction default standing (`/npcaddon default`).
- Release v0.1.0 (pre-release) published on GitHub. Untested: faction default standing with a second player.
- 2026-11-23: v0.2.0 released: `come` removed, compass facing for form/defend/follow (verified in game).
- Ideas not started: player as faction member (via faction points), cross-dimension following, GUI polish (auto-select first squad, trim empty space).

## Next: Control and feel (planned, not started)

### Command wand
- Item: left-click selects a squad, right-click gives an order ("move here" on ground, "attack that" on an entity).
- Also covers the wand-based NPC selection skipped in step 4.
- Server validates the order (squad exists, player owns/controls it, target in range).

### Stances
- Per-squad stance: aggressive, defensive, hold fire, passive.
- Implement through the NPC retaliate setting (`INPCAi.setRetaliateType`) plus attack/aggro range; restore previous values when the stance changes.
- Show the current stance in the GUI and allow changing it per squad.

### Squad HUD
- On-screen overlay for the active squad: names and health bars.
- Markers showing where squad members are (in world or on screen edge).
- Client-side only; sync NPC health/position from the server as needed.

## Later: Ancient Warfare 2 faction relations (decided, not started; after the wand, stances and HUD)
Target: `ancientwarfare-0.2.9-remastered.jar` (modId `ancientwarfare`, GPL-3.0, Forge 47). Optional dependency: compile-only jar in `libs/`, never bundled, no AW classes loaded when AW is absent.

Findings (from the jar, not yet run in game):
- AW factions are plain names (`nogg`, `empire`, `orc`...) from the datapack file `data/ancientwarfare/npc/factions.json`; `FactionRegistry.factionNames()` and `isHostile(a, b)` exist; an AW NPC reports its faction via `NpcFactionAccess.getFactionName()`.
- AW compares factions only between AW NPCs. For other entities it targets by entity-type tag, and every CustomNPCs NPC is the single type `customnpcs:customnpc`, so AW cannot tell CustomNPCs factions apart. The addon has to add that.

Decisions:
- Relations are ALWAYS mutual (hostile, neutral, allied), for CustomNPCs <-> CustomNPCs and for CustomNPCs <-> AW. No one-way option.
- Allied = they simply stop fighting (no joining each other's fights).
- Relations between a CustomNPCs faction and an AW faction are stored in the addon's own SavedData next to the ally list.
- Hostile: periodic server scan (about every second, only for factions with a relation) calls `setTarget` on nearby members of both sides; re-apply if AW's goals drop it. Allied/neutral: cancel targeting between the two sides with `LivingChangeTargetEvent`.
- GUI and `/npcaddon relation` list AW factions beside CustomNPCs ones and can edit relations with them.
- Players tab also lists AW factions and edits the player's standing with them; the starting values are AW's own defaults toward the player (`player_default_standing` in factions.json).
- AW <-> AW hostility stays exactly as AW defines it (not editable in the addon). May be discussed at a later date.
- Pairs of a CustomNPCs faction and an AW faction start neutral until set (assumption; confirm when building).

## Status (2026-11-24): stances and command wand written (builds, untested in game)
- Stances: `group/Stance`, `StanceManager` (retaliate type + aggro range, originals kept in the NPC's persistent data; `LivingChangeTargetEvent` filter for hold fire / passive; catch-up on chunk load), saved per squad in `GroupData`, `/npcaddon group stance`, cycle button under the member list in the Squads tab.
- Defensive = the NPC's own settings (no extra changes). Aggressive = aggro range doubled (24..64). Hold fire = retaliate normal, only the recent attacker is allowed as a target. Passive = retaliate "nothing", no targets at all. Explicit attack orders always pass.
- Wand: `npcaddon:command_wand` (creative Tools tab), clicks cancelled client-side and sent as `WandPacket`; `WandController` does a 64-block pick. Left-click NPC = select squad (again = next squad it is in), left-click anything else = deselect; right-click ground = `group moveto`, entity = attack, sneak = follow/stop toggle. Action bar shows the selected squad and stance.
- To test: stance effects per mode, stance survives relog/chunk reload, wand reach and each click type, nothing breaks blocks with the wand, GUI stance button.

## Status (2026-11-24, later): monster hostility and area attack written (builds, untested in game)
- Stances and wand verified in game by the user.
- `group/MobDefense`: once a second, idle squad NPCs (aggressive/defensive stance, not walking a formation, no area order) target the nearest visible `Enemy` mob within their aggro range.
- Area attack: `GroupMover.areaAttack` + `tickAreas`, `/npcaddon group attackarea <name> [radius 2..48] [x y z]`, GUI "Area" button (shares the patrol radius box, centred on where the player aims), wand Ctrl + right-click (uses the last GUI radius, default 10). Valid targets: `Enemy` mobs and NPCs whose faction is hostile to the squad NPC (`faction.isAggressiveToNpc`); never players or animals. Flow: walk to a slot at the centre, engage targets inside the radius, when clear walk back and hold; targets that flee more than radius + 12 away are dropped. Lasts until another order or stop.
- To test: squads fight zombies/skeletons unprompted; hold fire/passive do not; area attack from GUI, command and wand; they hold the spot afterwards and engage new monsters; stop ends it.
