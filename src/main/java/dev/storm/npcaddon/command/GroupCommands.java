package dev.storm.npcaddon.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.aw.AwSiege;
import dev.storm.npcaddon.group.Formation;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.GroupMover;
import dev.storm.npcaddon.group.SiegeCrew;
import dev.storm.npcaddon.group.Stance;
import dev.storm.npcaddon.group.StanceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /npcaddon group add|remove|delete|list|form|defend|attack|patrol|follow|stop. */
final class GroupCommands {
    private static final double MAX_RADIUS = 64;

    private GroupCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("group")
            .then(Commands.literal("add")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, MAX_RADIUS))
                        .executes(c -> add(c.getSource(), StringArgumentType.getString(c, "name"),
                            DoubleArgumentType.getDouble(c, "radius"))))))
            .then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, MAX_RADIUS))
                        .executes(c -> remove(c.getSource(), StringArgumentType.getString(c, "name"),
                            DoubleArgumentType.getDouble(c, "radius"))))))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> delete(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("list").executes(c -> list(c.getSource())))
            .then(Commands.literal("form")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("formation", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            for (Formation f : Formation.values()) sb.suggest(f.name().toLowerCase(Locale.ROOT));
                            return sb.buildFuture();
                        })
                        .executes(c -> form(c.getSource(), StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "formation"), 2.0, null))
                        .then(Commands.argument("spacing", DoubleArgumentType.doubleArg(1, 8))
                            .executes(c -> form(c.getSource(), StringArgumentType.getString(c, "name"),
                                StringArgumentType.getString(c, "formation"),
                                DoubleArgumentType.getDouble(c, "spacing"), null))
                            .then(facingArg()
                                .executes(c -> form(c.getSource(), StringArgumentType.getString(c, "name"),
                                    StringArgumentType.getString(c, "formation"),
                                    DoubleArgumentType.getDouble(c, "spacing"),
                                    StringArgumentType.getString(c, "facing"))))))))
            .then(Commands.literal("defend")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> defend(c.getSource(), StringArgumentType.getString(c, "name"), null))
                    .then(facingArg()
                        .executes(c -> defend(c.getSource(), StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "facing"))))))
            .then(Commands.literal("attack")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> attack(c.getSource(), StringArgumentType.getString(c, "name"),
                            EntityArgument.getEntity(c, "target"))))))
            .then(Commands.literal("patrol")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(2, MAX_RADIUS))
                        .executes(c -> patrol(c.getSource(), StringArgumentType.getString(c, "name"),
                            DoubleArgumentType.getDouble(c, "radius"))))))
            .then(Commands.literal("follow")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> follow(c.getSource(), StringArgumentType.getString(c, "name"), null))
                    .then(facingArg()
                        .executes(c -> follow(c.getSource(), StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "facing"))))))
            .then(Commands.literal("stop")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> stop(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("attackarea")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> attackArea(c.getSource(), StringArgumentType.getString(c, "name"), 10, null))
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(2, 48))
                        .executes(c -> attackArea(c.getSource(), StringArgumentType.getString(c, "name"),
                            DoubleArgumentType.getDouble(c, "radius"), null))
                        .then(Commands.argument("pos", Vec3Argument.vec3(false))
                            .executes(c -> attackArea(c.getSource(), StringArgumentType.getString(c, "name"),
                                DoubleArgumentType.getDouble(c, "radius"), Vec3Argument.getVec3(c, "pos")))))))
            .then(Commands.literal("manweapons")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> manWeapons(c.getSource(), StringArgumentType.getString(c, "name"), 24))
                    .then(Commands.argument("radius", DoubleArgumentType.doubleArg(4, MAX_RADIUS))
                        .executes(c -> manWeapons(c.getSource(), StringArgumentType.getString(c, "name"),
                            DoubleArgumentType.getDouble(c, "radius"))))))
            .then(Commands.literal("manweapon")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("weapon", EntityArgument.entity())
                        .executes(c -> manWeapon(c.getSource(), StringArgumentType.getString(c, "name"),
                            EntityArgument.getEntity(c, "weapon"))))))
            .then(Commands.literal("bombard")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("pos", Vec3Argument.vec3(false))
                        .executes(c -> bombard(c.getSource(), StringArgumentType.getString(c, "name"),
                            Vec3Argument.getVec3(c, "pos"), 0))
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(0, 32))
                            .executes(c -> bombard(c.getSource(), StringArgumentType.getString(c, "name"),
                                Vec3Argument.getVec3(c, "pos"), DoubleArgumentType.getDouble(c, "radius")))))))
            .then(Commands.literal("moveto")
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("pos", Vec3Argument.vec3(false))
                        .executes(c -> moveTo(c.getSource(), StringArgumentType.getString(c, "name"),
                            Vec3Argument.getVec3(c, "pos"), null))
                        .then(facingArg()
                            .executes(c -> moveTo(c.getSource(), StringArgumentType.getString(c, "name"),
                                Vec3Argument.getVec3(c, "pos"), StringArgumentType.getString(c, "facing")))))))
            .then(Commands.literal("stance")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> stance(c.getSource(), StringArgumentType.getString(c, "name"), null))
                    .then(Commands.argument("stance", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            for (Stance st : Stance.values()) sb.suggest(st.key);
                            return sb.buildFuture();
                        })
                        .executes(c -> stance(c.getSource(), StringArgumentType.getString(c, "name"),
                            StringArgumentType.getString(c, "stance"))))));
    }

    private static final String[] COMPASS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    /** Optional compass facing: degrees (0 = north, 90 = east) or a compass word. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> facingArg() {
        return Commands.argument("facing", StringArgumentType.word()).suggests((c, sb) -> {
            for (String k : COMPASS) sb.suggest(k);
            return sb.buildFuture();
        });
    }

    /** Compass text to Minecraft yaw (0 = north becomes 180), or null after telling the executor it is invalid. */
    private static Float parseFacing(CommandSourceStack s, String text) {
        double deg;
        switch (text.toLowerCase(Locale.ROOT)) {
            case "n", "north" -> deg = 0;
            case "ne" -> deg = 45;
            case "e", "east" -> deg = 90;
            case "se" -> deg = 135;
            case "s", "south" -> deg = 180;
            case "sw" -> deg = 225;
            case "w", "west" -> deg = 270;
            case "nw" -> deg = 315;
            default -> {
                try {
                    deg = Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    deg = Double.NaN;
                }
            }
        }
        if (!Double.isFinite(deg)) {
            s.sendFailure(Component.literal("Facing must be degrees (0 = north, 90 = east) or n, ne, e, se, s, sw, w, nw"));
            return null;
        }
        return net.minecraft.util.Mth.wrapDegrees((float) deg + 180f);
    }

    /** The squad member nearest the player answers in chat. */
    private static void reply(CommandSourceStack s, List<EntityNPCInterface> npcs, String text) {
        if (!(s.getEntity() instanceof ServerPlayer p)) return;
        EntityNPCInterface best = null;
        for (EntityNPCInterface n : npcs) {
            if (n.level() != p.level()) continue;
            if (best == null || n.distanceToSqr(p) < best.distanceToSqr(p)) best = n;
        }
        if (best == null) return;
        p.sendSystemMessage(Component.literal("<" + best.getName().getString() + "> " + text));
    }

    private static GroupData data(CommandSourceStack s) {
        return GroupData.get(s.getServer().overworld());
    }

    private static List<EntityNPCInterface> nearby(CommandSourceStack s, double radius) {
        Vec3 p = s.getPosition();
        return s.getLevel().getEntitiesOfClass(EntityNPCInterface.class,
            new net.minecraft.world.phys.AABB(p, p).inflate(radius), e -> e.isAlive() && e.distanceToSqr(p) <= radius * radius);
    }

    private static int add(CommandSourceStack s, String name, double radius) {
        List<UUID> ids = new ArrayList<>();
        for (EntityNPCInterface n : nearby(s, radius)) ids.add(n.getUUID());
        if (ids.isEmpty()) {
            s.sendFailure(Component.literal("No NPCs within " + radius + " blocks"));
            return 0;
        }
        int added = data(s).addAll(name, ids);
        Stance st = data(s).stance(name);
        for (EntityNPCInterface n : nearby(s, radius)) StanceManager.apply(n, st);
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + added + " NPC(s) added ("
            + ids.size() + " in range)"), true);
        return added;
    }

    private static int remove(CommandSourceStack s, String name, double radius) {
        GroupData d = data(s);
        if (!d.exists(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return 0;
        }
        int removed = 0;
        for (EntityNPCInterface n : nearby(s, radius)) {
            if (d.removeMember(name, n.getUUID())) {
                SiegeCrew.release(n);
                removed++;
                StanceManager.apply(n, d.stanceOfMember(n.getUUID()));
            }
        }
        int r = removed;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + r + " NPC(s) removed"), true);
        return removed;
    }

    private static int delete(CommandSourceStack s, String name) {
        if (data(s).exists(name)) {
            for (UUID id : new ArrayList<>(data(s).members(name))) {
                EntityNPCInterface n = find(s, id);
                if (n != null) {
                    SiegeCrew.release(n);
                    StanceManager.apply(n, Stance.DEFENSIVE);
                }
            }
        }
        if (!data(s).removeGroup(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return 0;
        }
        s.sendSuccess(() -> Component.literal("Group '" + name + "' deleted"), true);
        return 1;
    }

    private static int list(CommandSourceStack s) {
        GroupData d = data(s);
        if (d.names().isEmpty()) {
            s.sendSuccess(() -> Component.literal("No groups"), false);
            return 0;
        }
        for (String name : d.names()) {
            int total = d.members(name).size();
            long loaded = d.members(name).stream().filter(id -> find(s, id) != null).count();
            s.sendSuccess(() -> Component.literal(name + ": " + total + " NPC(s), " + loaded + " loaded"), false);
        }
        return d.names().size();
    }

    private static EntityNPCInterface find(CommandSourceStack s, UUID id) {
        for (ServerLevel level : s.getServer().getAllLevels()) {
            if (level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive()) return n;
        }
        return null;
    }

    /** Forms the group in front of the executor, facing the given compass direction (default: the way they face). */
    private static int form(CommandSourceStack s, String name, String formationName, double spacing, String facing)
            throws CommandSyntaxException {
        GroupData d = data(s);
        if (!d.exists(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return 0;
        }
        Formation f;
        try {
            f = Formation.valueOf(formationName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            s.sendFailure(Component.literal("Formation must be line, column, wedge, circle or square"));
            return 0;
        }
        Entity self = s.getEntityOrException();
        List<EntityNPCInterface> npcs = new ArrayList<>();
        int skipped = 0;
        for (UUID id : d.members(name)) {
            EntityNPCInterface n = find(s, id);
            if (n == null || n.level() != self.level()) skipped++;
            else npcs.add(n);
        }
        if (npcs.isEmpty()) {
            s.sendFailure(Component.literal("No loaded NPCs of '" + name + "' in your dimension"));
            return 0;
        }
        float yaw = self.getYRot();
        // The formation is placed where the executor looks, but may face another way.
        double lookRad = Math.toRadians(yaw);
        Vec3 centre = self.position().add(-Math.sin(lookRad) * (spacing + 2), 0, Math.cos(lookRad) * (spacing + 2));
        if (facing != null) {
            Float y = parseFacing(s, facing);
            if (y == null) return 0;
            yaw = y;
        }
        for (int i = 0; i < npcs.size(); i++) {
            EntityNPCInterface npc = npcs.get(i);
            GroupMover.moveTo(npc, f.slot(centre, yaw, i, npcs.size(), spacing));
            GroupMover.face(npc, f.facing(yaw, i, npcs.size()));
        }
        reply(s, npcs, "Forming " + f.name().toLowerCase(Locale.ROOT));
        int m = npcs.size(), k = skipped;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) forming " + f.name().toLowerCase(Locale.ROOT)
            + (k > 0 ? ", " + k + " skipped (unloaded or other dimension)" : "")), true);
        return m;
    }

    /** Group members that are loaded, or null after telling the executor why not. */
    private static List<EntityNPCInterface> require(CommandSourceStack s, String name) {
        if (!data(s).exists(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return null;
        }
        List<EntityNPCInterface> npcs = new ArrayList<>();
        for (UUID id : data(s).members(name)) {
            EntityNPCInterface n = find(s, id);
            if (n != null) npcs.add(n);
        }
        if (npcs.isEmpty()) {
            s.sendFailure(Component.literal("No loaded NPCs in '" + name + "'"));
            return null;
        }
        return npcs;
    }

    /** NPCs walk to the executor's position and hold: home moves there and return-home is switched on. */
    private static int defend(CommandSourceStack s, String name, String facing) throws CommandSyntaxException {
        return hold(s, name, s.getEntityOrException().position(), facing, "Defending you", "defending this position");
    }

    /** Same as defend, at a chosen position (the command wand uses this). */
    private static int moveTo(CommandSourceStack s, String name, Vec3 pos, String facing) throws CommandSyntaxException {
        return hold(s, name, pos, facing, "Moving out", "moving to the marked position");
    }

    private static int hold(CommandSourceStack s, String name, Vec3 pos, String facing, String replyText, String what)
            throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        float yaw = self.getYRot();
        if (facing != null) {
            Float y = parseFacing(s, facing);
            if (y == null) return 0;
            yaw = y;
        }
        int n = 0;
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != self.level()) continue;
            npc.setTarget(null);
            GroupMover.moveTo(npc, Formation.SQUARE.slot(pos, yaw, n, npcs.size(), 2.0), true);
            GroupMover.face(npc, yaw);
            n++;
        }
        reply(s, npcs, replyText);
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) " + what), true);
        return n;
    }

    /**
     * Area attack: the squad walks to the centre (default: the executor), kills every hostile monster and
     * hostile-faction NPC inside the radius, and holds the spot afterwards, engaging newcomers too.
     */
    private static int attackArea(CommandSourceStack s, String name, double radius, Vec3 center)
            throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        Vec3 c = center != null ? center : self.position();
        int n = 0;
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != self.level()) continue;
            GroupMover.cancel(npc);
            npc.setTarget(null);
            GroupMover.areaAttack(npc, c, radius, Formation.SQUARE.slot(c, self.getYRot(), n, npcs.size(), 2.0));
            n++;
        }
        reply(s, npcs, "Clearing the area");
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) clearing a "
            + (int) radius + "-block radius"), true);
        return n;
    }

    private static boolean noSiege(CommandSourceStack s) {
        if (AwRelations.available()) return false;
        s.sendFailure(Component.literal("Siege weapons need Ancient Warfare"));
        return true;
    }

    /**
     * Free members crew the free siege weapons around the executor, nearest NPC to each. Members already crewing go
     * back to picking their own targets.
     */
    private static int manWeapons(CommandSourceStack s, String name, double radius) throws CommandSyntaxException {
        if (noSiege(s)) return 0;
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        List<EntityNPCInterface> free = new ArrayList<>();
        List<EntityNPCInterface> crews = new ArrayList<>();
        for (EntityNPCInterface n : npcs) {
            if (n.level() != self.level()) continue;
            if (SiegeCrew.isCrew(n)) {
                SiegeCrew.resumeAuto(n);
                crews.add(n);
            } else {
                free.add(n);
            }
        }
        Vec3 p = s.getPosition();
        List<Entity> weapons = new ArrayList<>(s.getLevel().getEntities((Entity) null,
            new net.minecraft.world.phys.AABB(p, p).inflate(radius),
            w -> AwSiege.isWeapon(w) && w.isAlive() && w.getPassengers().isEmpty() && !SiegeCrew.isClaimed(w)
                && w.distanceToSqr(p) <= radius * radius));
        weapons.sort(java.util.Comparator.comparingDouble(w -> w.distanceToSqr(p)));
        int n = 0;
        for (Entity weapon : weapons) {
            if (free.isEmpty()) break;
            EntityNPCInterface crew = free.stream()
                .min(java.util.Comparator.comparingDouble(f -> f.distanceToSqr(weapon))).get();
            free.remove(crew);
            SiegeCrew.man(crew, weapon, s.getPlayer());
            crews.add(crew);
            n++;
        }
        if (crews.isEmpty()) {
            s.sendFailure(Component.literal("No free siege weapon within " + (int) radius + " blocks"));
            return 0;
        }
        reply(s, crews, "Manning the weapons");
        int m = n, total = crews.size();
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) sent to siege weapons, "
            + total + " crewing in all"), true);
        return total;
    }

    /** The nearest free member crews this particular weapon. */
    private static int manWeapon(CommandSourceStack s, String name, Entity weapon) {
        if (noSiege(s)) return 0;
        if (!AwSiege.isWeapon(weapon)) {
            s.sendFailure(Component.literal("That is not a siege weapon NPCs can use"));
            return 0;
        }
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        if (!weapon.getPassengers().isEmpty() || SiegeCrew.isClaimed(weapon)) {
            s.sendFailure(Component.literal("That weapon already has a crew"));
            return 0;
        }
        EntityNPCInterface best = null;
        for (EntityNPCInterface n : npcs) {
            if (n.level() != weapon.level() || SiegeCrew.isCrew(n)) continue;
            if (best == null || n.distanceToSqr(weapon) < best.distanceToSqr(weapon)) best = n;
        }
        if (best == null) {
            s.sendFailure(Component.literal("No free NPC in '" + name + "' to crew it"));
            return 0;
        }
        SiegeCrew.man(best, weapon, s.getPlayer());
        reply(s, List.of(best), "Manning the weapon");
        String who = best.getName().getString();
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + who + " sent to the "
            + weapon.getName().getString()), true);
        return 1;
    }

    /** The squad's crews shoot at a spot (radius 0) or around it until another order. */
    private static int bombard(CommandSourceStack s, String name, Vec3 pos, double radius) {
        if (noSiege(s)) return 0;
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        List<EntityNPCInterface> firing = new ArrayList<>();
        int crews = 0;
        for (EntityNPCInterface n : npcs) {
            if (!SiegeCrew.isCrew(n)) continue;
            crews++;
            if (n.level() == s.getLevel() && SiegeCrew.bombard(n, pos, radius)) firing.add(n);
        }
        if (firing.isEmpty()) {
            s.sendFailure(Component.literal(crews == 0
                ? "'" + name + "' has no crews: use /npcaddon group manweapons " + name + " first"
                : "That spot is out of range of the squad's weapons"));
            return 0;
        }
        reply(s, firing, "Bombarding");
        int m = firing.size(), total = crews;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " of " + total + " crew(s) bombarding"), true);
        return m;
    }

    /** Shows or sets the squad's stance. */
    private static int stance(CommandSourceStack s, String name, String text) {
        GroupData d = data(s);
        if (!d.exists(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return 0;
        }
        if (text == null) {
            s.sendSuccess(() -> Component.literal("Group '" + name + "' stance: " + d.stance(name).label), false);
            return 1;
        }
        Stance st = Stance.parse(text);
        if (st == null) {
            s.sendFailure(Component.literal("Stance must be aggressive, defensive, hold or passive"));
            return 0;
        }
        d.setStance(name, st);
        List<EntityNPCInterface> npcs = new ArrayList<>();
        for (UUID id : d.members(name)) {
            EntityNPCInterface n = find(s, id);
            if (n == null) continue;
            StanceManager.apply(n, st);
            npcs.add(n);
        }
        reply(s, npcs, "Stance: " + st.label);
        int m = npcs.size();
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + st.label + " (" + m + " NPC(s) updated, "
            + "unloaded ones update when they load)"), true);
        return m;
    }

    private static int attack(CommandSourceStack s, String name, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            s.sendFailure(Component.literal("Target must be a living entity"));
            return 0;
        }
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        int n = 0;
        for (EntityNPCInterface npc : npcs) {
            if (npc == target || npc.level() != target.level()) continue;
            GroupMover.cancel(npc);
            // defend leaves return-to-home on, which drags the NPC back to its post mid-chase
            npc.ais.returnToStart = false;
            GroupMover.haltPatrol(npc);
            GroupMover.attack(npc, living);
            n++;
        }
        reply(s, npcs, "Attacking target");
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) attacking "
            + target.getName().getString()), true);
        return n;
    }

    /** Square loop of waypoints around the executor, each NPC starting at a different corner. */
    private static int patrol(CommandSourceStack s, String name, double radius) throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        ServerLevel level = s.getLevel();
        Vec3 c = self.position();
        int[][] corners = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        List<int[]> path = new ArrayList<>();
        for (int[] k : corners) {
            BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(c.x + k[0] * radius, c.y, c.z + k[1] * radius));
            path.add(new int[]{p.getX(), p.getY(), p.getZ()});
        }
        int n = 0;
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != level) continue;
            GroupMover.cancel(npc);
            npc.setTarget(null);
            npc.ais.setMovingPath(new ArrayList<>(path));
            npc.ais.setMovingPathType(0, false);
            npc.ais.setMovingPos(n % path.size());
            npc.ais.setMovingType(2);
            n++;
        }
        reply(s, npcs, "Patrolling the area");
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) patrolling a "
            + (int) (radius * 2) + "-block square"), true);
        return n;
    }

    /**
     * The squad trails the executor, each NPC with its own slot, until stop or another order. Standing still, they
     * face the way the executor faces, or a fixed compass direction when one is given.
     */
    private static int follow(CommandSourceStack s, String name, String facing) throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        Float fixed = null;
        if (facing != null) {
            fixed = parseFacing(s, facing);
            if (fixed == null) return 0;
        }
        int n = 0;
        List<EntityNPCInterface> followers = new ArrayList<>();
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != self.level()) continue;
            GroupMover.cancel(npc);
            npc.setTarget(null);
            GroupMover.follow(npc, self, n++);
            if (fixed != null) GroupMover.face(npc, fixed);
            else GroupMover.faceLeader(npc, self);
            followers.add(npc);
        }
        reply(s, followers, "Following you");
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) following you"), true);
        return n;
    }

    /** Cancels moves, attacks and patrols; NPCs stand where they are. */
    private static int stop(CommandSourceStack s, String name) {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        for (EntityNPCInterface npc : npcs) {
            GroupMover.cancel(npc);
            npc.setTarget(null);
            // The patrol goal keeps walking to its current waypoint until the AI rebuild lands, so drop the
            // waypoints too: an empty path falls back to the start position, which is where the NPC stands now.
            npc.ais.setStartPos(npc.blockPosition());
            npc.ais.clearMovingPath();
            npc.ais.setMovingPos(0);
            npc.ais.setMovingType(0);
            GroupMover.haltPatrol(npc);
        }
        reply(s, npcs, "Standing down");
        int m = npcs.size();
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) stopped"), true);
        return m;
    }
}
