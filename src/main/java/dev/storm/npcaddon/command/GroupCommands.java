package dev.storm.npcaddon.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.storm.npcaddon.group.Formation;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.GroupMover;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
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

/** /npcaddon group add|remove|delete|list|come. */
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
                            StringArgumentType.getString(c, "formation"), 2.0))
                        .then(Commands.argument("spacing", DoubleArgumentType.doubleArg(1, 8))
                            .executes(c -> form(c.getSource(), StringArgumentType.getString(c, "name"),
                                StringArgumentType.getString(c, "formation"),
                                DoubleArgumentType.getDouble(c, "spacing")))))))
            .then(Commands.literal("defend")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> defend(c.getSource(), StringArgumentType.getString(c, "name")))))
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
                    .executes(c -> follow(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("stop")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> stop(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("come")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> come(c.getSource(), StringArgumentType.getString(c, "name")))));
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
        for (EntityNPCInterface n : nearby(s, radius)) if (d.removeMember(name, n.getUUID())) removed++;
        int r = removed;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + r + " NPC(s) removed"), true);
        return removed;
    }

    private static int delete(CommandSourceStack s, String name) {
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

    private static int come(CommandSourceStack s, String name) throws CommandSyntaxException {
        GroupData d = data(s);
        if (!d.exists(name)) {
            s.sendFailure(Component.literal("No group '" + name + "'"));
            return 0;
        }
        Entity self = s.getEntityOrException();
        Vec3 centre = self.position();
        int moved = 0, skipped = 0;
        List<EntityNPCInterface> movers = new ArrayList<>();
        for (UUID id : d.members(name)) {
            EntityNPCInterface n = find(s, id);
            if (n == null || n.level() != self.level()) {
                skipped++;
                continue;
            }
            GroupMover.moveTo(n, GroupMover.slot(centre, moved));
            movers.add(n);
            moved++;
        }
        reply(s, movers, "Coming to you");
        int m = moved, k = skipped;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) moving to you"
            + (k > 0 ? ", " + k + " skipped (unloaded or other dimension)" : "")), true);
        return moved;
    }

    /** Forms the group in front of the executor, facing the way they face. */
    private static int form(CommandSourceStack s, String name, String formationName, double spacing)
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
        double yawRad = Math.toRadians(yaw);
        Vec3 centre = self.position().add(-Math.sin(yawRad) * (spacing + 2), 0, Math.cos(yawRad) * (spacing + 2));
        for (int i = 0; i < npcs.size(); i++) {
            GroupMover.moveTo(npcs.get(i), f.slot(centre, yaw, i, npcs.size(), spacing));
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
    private static int defend(CommandSourceStack s, String name) throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        int n = 0;
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != self.level()) continue;
            npc.setTarget(null);
            GroupMover.moveTo(npc, Formation.SQUARE.slot(self.position(), self.getYRot(), n, npcs.size(), 2.0), true);
            n++;
        }
        reply(s, npcs, "Defending you");
        int m = n;
        s.sendSuccess(() -> Component.literal("Group '" + name + "': " + m + " NPC(s) defending this position"), true);
        return n;
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

    /** The squad trails the executor, each NPC with its own slot, until stop or another order. */
    private static int follow(CommandSourceStack s, String name) throws CommandSyntaxException {
        List<EntityNPCInterface> npcs = require(s, name);
        if (npcs == null) return 0;
        Entity self = s.getEntityOrException();
        int n = 0;
        List<EntityNPCInterface> followers = new ArrayList<>();
        for (EntityNPCInterface npc : npcs) {
            if (npc.level() != self.level()) continue;
            GroupMover.cancel(npc);
            npc.setTarget(null);
            GroupMover.follow(npc, self, n++);
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
