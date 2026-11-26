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
2. [DONE] Faction relations + ally list + GUI (simplest, uses the public API).
3. [DONE] Keybind + GUI screen skeleton + packets.
4. [DONE] NPC groups + "move to me" command.
5. [DONE] Formations.
6. [DONE] Attack / defend / patrol.

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

## Control and feel (done, released in v0.3.0)

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

## Ancient Warfare 2 faction relations (done, released in v0.4.0)
Target: `ancientwarfare-0.2.9-remastered.jar` (modId `ancientwarfare`, GPL-3.0, Forge 47). Optional dependency: compile-only jar in `libs/`, never bundled, no AW classes loaded when AW is absent.

Findings (from the jar):
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

## Status (2026-11-24, later still): squad HUD written (builds, untested in game)
- `network/HudPacket` (server -> client, id 6) sent by `group/HudSync` every 10 ticks to players who have a squad selected; one empty packet clears it. Selection comes from the wand or from clicking a squad in the Squads tab (`Action.SELECT`), kept in `WandController.SELECTED`.
- Client: `ClientHud` (state + on/off), `HudOverlay` (registered with `RegisterGuiOverlaysEvent`): panel top-left (name, stance, up to 10 rows of name / health bar / order), a down-pointing marker above each member in view (colour = order, drawn through walls), a rotated arrow with distance on the screen edge for members out of view or behind. Live entity positions where the NPC is rendered, synced positions otherwise.
- Keybind `H` (rebindable, category "NPC Addon") toggles the HUD and shows "Squad HUD on/off" on the action bar.
- To test: select a squad with the wand and in the GUI; panel rows and health; markers follow NPCs; arrows point correctly for members to the sides, behind and far away; H toggles; HUD clears on deselect or squad delete; members in another dimension are listed but get no marker.

## Status (2026-11-24, end of session)
- v0.3.0 released (pre-release): stances, command wand, monster hostility, area attack, squad HUD. All verified in game.
- "Control and feel" section is complete. Next up: Ancient Warfare faction relations (see its section above). Still untested: faction default standing with a second player.

## Status (2026-11-24, AW): Ancient Warfare faction relations written (builds, untested in game)
- Build: `libs/ancientwarfare-0.2.9.jar` and `libs/geckolib-4.8.3.jar` (gitignored, copied from the user's own files) as `implementation fg.deobf(...)` for dev; mods.toml has an optional (non-mandatory) dependency on `ancientwarfare`.
- `aw/AwCompat` (is AW loaded), `AwBridge` (the ONLY class touching AW types: `FactionRegistry`, `FactionStandingData`, `NpcFactionAccess`; only call after `AwCompat.loaded()`), `AwRelationData` (SavedData, "customId|awName" -> hostile/allied, neutral = absent, so always mutual), `AwRelations` (set/get, AW player standing: hostile -50, neutral 0, friendly 50; AW is hostile below 0, standing stored in the overworld), `AwEvents` (allied pairs: `LivingChangeTargetEvent` cancel; hostile pairs: once a second idle NPCs on both sides target the nearest visible member of the other side).
- Commands `/npcaddon awrelation`, `awplayer`, `awfactions`. GUI: AW factions are listed after the CustomNPCs ones (tag "AW") in the Factions and Players tabs; `FactionListPacket.Info` gained `aw` and `awDefault`; `SetRelationPacket` and `PlayerStandingPacket` carry AW names. AW<->AW relations and AW default standings are not editable (message shown). The Players tab shows the selected AW faction's default standing.
- Open risks to test: AW's own goals may drop or ignore a target we set (hostile pair); AW NPCs retaliating normally when hit; the addon must still load without AW installed (guarded, but only tested with AW present); AW + GeckoLib 4.8.3 loading in the dev client.

## Status (2026-11-25): v0.4.0 released
- Ancient Warfare relations verified in game and released as v0.4.0 (pre-release). Untested: loading without AW installed.
- Ideas for later: AW<->AW hostility editing, player as faction member, cross-dimension following, GUI polish.

## Smarter squads

### Ranged behind melee (done, released in v0.6.0)
- Formations place ranged NPCs at the back and melee NPCs at the front.
- Ranged = a projectile is set. `Formation.arrange` places them; `group/RangedSupport` backs them away with the move control. The tactical settings were not used: CustomNPCs tactic goals drop the target when they stop, and none of them holds a distance.
- 2026-11-25: formations and GUI verified in game. Fixed after testing (verified): move/defend/area now use the squad's last formation (saved in `GroupData`); squad NPCs retarget to the closest enemy (`MobDefense`, area attack; `group/Hostility`); backing away moves the NPC directly with a four-corner ground check (it walked off ledges before).

### Retreat and rally (done, released in v0.6.0)
- `group/Retreat`, default threshold 10% (user's choice), per squad in `GroupData`; net protocol "7".
- NPCs flee when health drops below a threshold (the mod already has panic/avoid AI to build on).
- A rally order brings fleeing NPCs back to the squad once they have recovered or the threat is gone.
- Per-squad setting for the retreat threshold, and an option to turn it off.

### Medic role (done, released in v0.6.0)
- `group/Medic` heals by itself instead of using `JobHealer`: that job only applies potion effects to everyone nearby and would replace the NPC's own job. Per squad medics in `GroupData`; net protocol "8".
- Mark one or more squad members as medics, using the mod's built-in healer job (`JobHealer`).
- Medics stay behind the front line and heal squad members.

## Later: Bigger systems (planned, not started)

### Recruiting (done, verified in game 2026-11-25, unreleased)
- User decisions: any player commands own squads; right-click window; types limit abilities only (mounted ride, engineers crew; weapons are given by the operator); one-off payment in gold ingots, permanent. Prices melee 2, ranged 5, mounted 7, engineer 8.
- Right-click version verified, then replaced at the user's request (verified): Recruit tab (`client/HireScreen`), operator-saved templates (`RecruitCatalog`), hired copies arrive from afar (`group/Arrivals`), mounted ones on a new horse, 30 s cooldown; protocol "12".
- 2026-11-26 (verified): spawn distance 1.5x the original, 27-72 blocks; dismissed hired copies walk away and vanish (`group/Departures`).
- `group/Recruits`, `RecruitType`, `client/RecruitScreen`; squad owners in `GroupData`; net protocol "11". Own hire system instead of the follower role.
- Hire NPCs into a squad for items or currency, building on the follower role's hire system (`IRoleFollower`).
- Server validates payment before the NPC joins the squad.

### Reinforcements
- Call in extra NPCs when a fight starts or on a player order, using the mod's spawner job (`IJobSpawner`) or addon-side spawning.
- Cap on reinforcements per squad and a cooldown between calls.

### Mounted squads (done, verified in game 2026-11-25, unreleased)
- User decisions: vanilla horses and CustomNPCs mounts; Mount order without saddle; Dismount order.
- `group/Mounts`; vanilla horses are steered through the rider's navigation, CustomNPCs mounts mirror the rider's path target. Net protocol "9".
- First test (2026-11-25): wand could not target gates (their proxy blocks are hit first), riders faced sideways, NPC mounts were attacked (CustomNPCs' editor offers no mount control switch here), wheels barely turned. Fixed and verified: `AwSiege.gateAt`, rider/mount alignment, friendly NPC right-click mounts, `VehicleInputPacket` (protocol "10").
- Cavalry: squads that ride mounts, using the NPC mount-control option (`INPCAi.setMountControl`).
- Needs testing first: formations and patrols may behave differently while mounted.

## Driving siege weapons and battering ram (done, verified in game 2026-11-25, unreleased)
- User decisions: normal orders drive (stop halts, Dismount gets off); the ram attacks ordered targets only.
- `group/VehicleDriver` steers with `ChestPushcartEntity.setVehicleInput`, using the driver's path; step height 1 while driven. Boats excluded. `AwSiegeBridge` gained ram and gate handling.

## Siege crews (done, released in v0.5.0)
- Decisions: squad order; unlimited ammo by default, changeable in the mod config (the only config option for now); fire from a fixed spot (no driving); automatic targets and spot/area bombardment. Battering ram left out.
- `Config` (`config/npcaddon-common.toml`), `aw/AwSiege` (guarded) + `aw/AwSiegeBridge` (AW's `SiegeWeaponControl`; unlimited ammo restores the weapon inventory after each shot), `group/SiegeCrew` (walk, mount, fire every 80 ticks at most, bombardment aims at an armor stand that is never spawned, friendly safety radius 5 for automatic fire). Any GroupMover order releases the crew. Crew weapon and bombard spot/radius are saved on the NPC.
- Commands `group manweapons|manweapon|bombard`, wand right-click on a weapon / Alt + right-click, GUI Man and Bombard buttons. Net protocol "5".
- Siege checklist verified in game by the user. Added `client/WandMarker` (ring and post while Ctrl/Alt held with the wand) sharing `group/WandAim` with the server; radius synced in `HudPacket`; net protocol "6".
- 2026-11-25: siege crews, bombard-spot persistence and wand marker verified in game.
- 2026-11-25: loading and playing without AW verified (`./gradlew runClient -PnoAw`, folder `run-noaw`). Released v0.5.0.

## Smaller ideas
- GUI polish (done, verified 2026-11-25): Squads tab selects a squad on opening, empty panel space trimmed, tabs stay in place between screens.
- Not started: AW <-> AW hostility editing (to be discussed). Player membership, cross-dimension following and siege boats are done (see v0.7.0 additions).
- Untested: faction default standing with a second player.

## Fixes
- 2026-11-26 (verified): long marches scattered cavalry. `GroupMover` now puts move targets on the ground, travels in 14-block legs with detours and gives up only without progress (was 30 s / 3 stalls).

## v0.7.0 additions (2026-11-26, done, verified in game, unreleased)
- User decisions: players choose their faction in the menu (op opens factions, optional points requirement); membership sets standings from relations, makes faction squads defend the player and lets members command faction squads; CustomNPCs factions only; followers cross dimensions with mounts, siege weapons stay.
- Membership: `faction/Membership`, `MembershipData`, `group/MemberDefense`, `client/JoinScreen`; protocol "13". Members never command player-owned squads, and only owners/operators delete squads.
- Dimensions: `group/Crossing`. Boats: siege boats are mobile; `VehicleDriver` sails them straight, steering around land.
