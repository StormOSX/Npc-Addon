package dev.storm.npcaddon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.storm.npcaddon.faction.Membership;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /npcaddon join <faction>, leave (any player); faction open <id> [points], faction close <id> (operators). */
final class MemberCommands {
    private MemberCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> join() {
        return Commands.literal("join")
            .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                .executes(c -> Membership.join(c.getSource().getPlayerOrException(),
                    IntegerArgumentType.getInteger(c, "faction")) ? 1 : 0));
    }

    static LiteralArgumentBuilder<CommandSourceStack> leave() {
        return Commands.literal("leave")
            .executes(c -> Membership.leave(c.getSource().getPlayerOrException(), true) ? 1 : 0);
    }

    static LiteralArgumentBuilder<CommandSourceStack> faction() {
        return Commands.literal("faction")
            .then(Commands.literal("open")
                .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                    .executes(c -> open(c.getSource(), IntegerArgumentType.getInteger(c, "faction"), 0))
                    .then(Commands.argument("points", IntegerArgumentType.integer(0))
                        .executes(c -> open(c.getSource(), IntegerArgumentType.getInteger(c, "faction"),
                            IntegerArgumentType.getInteger(c, "points"))))))
            .then(Commands.literal("close")
                .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                    .executes(c -> open(c.getSource(), IntegerArgumentType.getInteger(c, "faction"), -1))));
    }

    private static int open(CommandSourceStack s, int faction, int points) {
        if (!Membership.setOpen(s.getServer(), faction, points)) {
            s.sendFailure(Component.literal("Unknown faction id " + faction));
            return 0;
        }
        String what = points < 0 ? "closed to joining" : "open to join (" + points + " points needed)";
        s.sendSuccess(() -> Component.literal("Faction " + faction + " is " + what), true);
        return 1;
    }
}
