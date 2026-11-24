package dev.storm.npcaddon.network;

import dev.storm.npcaddon.group.Formation;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.Stance;
import dev.storm.npcaddon.group.WandController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Client -> server: an order from the squad screen. Validated here, then run as the matching
 * /npcaddon group command as the player, so permissions and behaviour are identical to typing it.
 */
public record SquadOrderPacket(Action action, String group, double value, String text) {
    public enum Action { FACTIONS, REFRESH, ADD, REMOVE, DELETE, FOLLOW, DEFEND, ATTACK, PATROL, STOP, FORM, STANCE, AREA }

    private static final double REACH = 64;

    public static void encode(SquadOrderPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeUtf(p.group, 32);
        buf.writeDouble(p.value);
        buf.writeUtf(p.text, 16);
    }

    public static SquadOrderPacket decode(FriendlyByteBuf buf) {
        return new SquadOrderPacket(buf.readEnum(Action.class), buf.readUtf(32), buf.readDouble(), buf.readUtf(16));
    }

    public static void handle(SquadOrderPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (p.action == Action.FACTIONS) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), FactionListPacket.build(player.server));
                return;
            }
            if (p.action != Action.REFRESH) run(player, p);
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), SquadListPacket.build(player.server));
        });
        ctx.setPacketHandled(true);
    }

    private static String num(double v, double min, double max) {
        double d = Double.isFinite(v) ? v : min;
        return String.format(Locale.ROOT, "%.1f", Math.max(min, Math.min(max, d)));
    }

    /** " <facing>" for the command when the client sent a valid compass direction, otherwise nothing. */
    private static String facing(String text) {
        String t = text.trim();
        return t.matches("(?i)[0-9]{1,3}|n|ne|e|se|s|sw|w|nw") ? " " + t : "";
    }

    private static void run(ServerPlayer player, SquadOrderPacket p) {
        if (!p.group.matches("[A-Za-z0-9_]{1,32}")) {
            player.sendSystemMessage(Component.literal("Squad names use letters, digits and underscores only"));
            return;
        }
        String g = p.group;
        String cmd;
        switch (p.action) {
            case ADD -> cmd = "npcaddon group add " + g + " " + num(p.value, 1, 64);
            case REMOVE -> cmd = "npcaddon group remove " + g + " " + num(p.value, 1, 64);
            case DELETE -> cmd = "npcaddon group delete " + g;
            case FOLLOW -> cmd = "npcaddon group follow " + g + facing(p.text);
            case DEFEND -> cmd = "npcaddon group defend " + g + facing(p.text);
            case STOP -> cmd = "npcaddon group stop " + g;
            case AREA -> {
                double radius = Double.parseDouble(num(p.value, 2, 48));
                WandController.rememberRadius(player, radius);
                Vec3 at = player.position();
                HitResult hit = player.pick(REACH, 1.0F, false);
                if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                    at = Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection()));
                }
                cmd = String.format(Locale.ROOT, "npcaddon group attackarea %s %.1f %.2f %.2f %.2f", g, radius,
                    at.x, at.y, at.z);
            }
            case STANCE -> {
                Stance st = Stance.parse(p.text);
                if (st == null) return;
                cmd = "npcaddon group stance " + g + " " + st.key;
            }
            case PATROL -> cmd = "npcaddon group patrol " + g + " " + num(p.value, 2, 64);
            case FORM -> {
                // text = "<formation>" or "<formation> <facing>"
                String[] parts = p.text.split(" ", 2);
                Formation f;
                try {
                    f = Formation.valueOf(parts[0].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return;
                }
                cmd = "npcaddon group form " + g + " " + f.name().toLowerCase(Locale.ROOT) + " " + num(p.value, 1, 8)
                    + (parts.length > 1 ? facing(parts[1]) : "");
            }
            case ATTACK -> {
                LivingEntity target = aimedAt(player, g);
                if (target == null) {
                    player.sendSystemMessage(Component.literal("Aim at a target first"));
                    return;
                }
                cmd = "npcaddon group attack " + g + " " + target.getUUID();
            }
            default -> {
                return;
            }
        }
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
    }

    /** The living entity under the player's crosshair, never one of the squad itself. */
    private static LivingEntity aimedAt(ServerPlayer player, String group) {
        GroupData data = GroupData.get(player.server.overworld());
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(REACH));
        AABB box = player.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable()
                && !data.members(group).contains(e.getUUID()),
            REACH * REACH);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }
}
