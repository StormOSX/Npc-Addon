package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

/**
 * Applies a stance to an NPC through CustomNPCs' retaliate type and aggro range, and filters the targets NPCs pick
 * by themselves. The NPC's original settings and its current stance live in its own persistent data, so they survive
 * restarts and can always be restored.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class StanceManager {
    private static final String ORIG = "npcaddon_stance_orig";
    private static final String CUR = "npcaddon_stance";
    private static final int RETALIATE_NORMAL = 0;
    private static final int RETALIATE_NOTHING = 3;
    private static final int MAX_AGGRO = 64;
    /** Hold fire still answers an attacker for this long after being hurt. */
    private static final int RETALIATE_TICKS = 200;

    private StanceManager() {}

    public static Stance of(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(CUR)) return Stance.DEFENSIVE;
        int i = pd.getInt(CUR);
        Stance[] all = Stance.values();
        return i >= 0 && i < all.length ? all[i] : Stance.DEFENSIVE;
    }

    public static void apply(EntityNPCInterface npc, Stance stance) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(ORIG)) {
            CompoundTag o = new CompoundTag();
            o.putInt("ret", npc.ais.getRetaliateType());
            o.putInt("aggro", npc.stats.aggroRange);
            pd.put(ORIG, o);
        }
        CompoundTag o = pd.getCompound(ORIG);
        int ret = o.getInt("ret");
        int aggro = o.getInt("aggro");
        switch (stance) {
            case AGGRESSIVE -> {
                ret = RETALIATE_NORMAL;
                aggro = Math.min(MAX_AGGRO, Math.max(aggro * 2, 24));
            }
            case HOLD_FIRE -> ret = RETALIATE_NORMAL;
            case PASSIVE -> ret = RETALIATE_NOTHING;
            default -> {}
        }
        if (npc.ais.getRetaliateType() != ret) npc.ais.setRetaliateType(ret);
        if (npc.stats.aggroRange != aggro) npc.stats.setAggroRange(aggro);
        if (stance == Stance.DEFENSIVE) {
            pd.remove(ORIG);
            pd.remove(CUR);
        } else {
            pd.putInt(CUR, stance.ordinal());
        }
    }

    /** True when an explicit order or a fresh attacker justifies this target for a restrained NPC. */
    private static boolean allowed(EntityNPCInterface npc, LivingEntity target, Stance s) {
        if (GroupMover.isOrderedTarget(npc, target)) return true;
        return s == Stance.HOLD_FIRE && target == npc.getLastHurtByMob()
            && npc.tickCount - npc.getLastHurtByMobTimestamp() < RETALIATE_TICKS;
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof EntityNPCInterface npc) || npc.level().isClientSide) return;
        LivingEntity target = e.getNewTarget();
        if (target == null) return;
        Stance s = of(npc);
        if (s != Stance.HOLD_FIRE && s != Stance.PASSIVE) return;
        if (!allowed(npc, target, s)) e.setCanceled(true);
    }

    /** An NPC that was unloaded while its squad's stance changed catches up when it loads again. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getEntity() instanceof EntityNPCInterface npc) || !(e.getLevel() instanceof ServerLevel level)) return;
        GroupData data = GroupData.get(level.getServer().overworld());
        Stance want = data.stanceOfMember(npc.getUUID());
        if (want != of(npc)) apply(npc, want);
    }
}
