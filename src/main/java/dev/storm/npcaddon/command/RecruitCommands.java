package dev.storm.npcaddon.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.RecruitType;
import dev.storm.npcaddon.group.Recruits;
import dev.storm.npcaddon.group.WandAim;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.Collection;
import java.util.List;

/** /npcaddon recruit <melee|ranged|mounted|engineer|none> [targets]; without targets, the NPC being looked at. */
final class RecruitCommands {
    private RecruitCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("recruit");
        for (RecruitType type : RecruitType.values()) root.then(option(type.key(), type));
        return root.then(option("none", null));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> option(String word, RecruitType type) {
        return Commands.literal(word)
            .executes(c -> aimed(c.getSource(), type))
            .then(Commands.argument("targets", EntityArgument.entities())
                .executes(c -> apply(c.getSource(), type, EntityArgument.getEntities(c, "targets"))));
    }

    private static int aimed(CommandSourceStack s, RecruitType type) throws CommandSyntaxException {
        Entity target = WandAim.of(s.getPlayerOrException(), 1.0F).living();
        if (!(target instanceof EntityNPCInterface)) {
            s.sendFailure(Component.literal("Look at an NPC, or name the targets"));
            return 0;
        }
        return apply(s, type, List.of(target));
    }

    private static int apply(CommandSourceStack s, RecruitType type, Collection<? extends Entity> targets) {
        GroupData data = GroupData.get(s.getServer().overworld());
        int n = 0;
        int busy = 0;
        for (Entity e : targets) {
            if (!(e instanceof EntityNPCInterface npc)) continue;
            Recruits.setType(npc, type);
            if (!data.squadsOf(npc.getUUID()).isEmpty()) busy++;
            n++;
        }
        if (n == 0) {
            s.sendFailure(Component.literal("No NPCs among the targets"));
            return 0;
        }
        String what = type == null ? "no longer recruits"
            : type.label + " recruits (" + type.price + " gold ingots)";
        String note = type != null && busy > 0 ? "; " + busy + " already in a squad and not for hire until removed" : "";
        int count = n;
        s.sendSuccess(() -> Component.literal(count + " NPC(s) are now " + what + note), true);
        return n;
    }
}
