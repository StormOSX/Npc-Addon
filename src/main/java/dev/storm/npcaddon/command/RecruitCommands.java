package dev.storm.npcaddon.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.storm.npcaddon.group.RecruitCatalog;
import dev.storm.npcaddon.group.RecruitType;
import dev.storm.npcaddon.group.Recruits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** /npcaddon recruit save <type> <name> (the NPC looked at), delete <name>, list. */
final class RecruitCommands {
    private RecruitCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> save = Commands.literal("save");
        for (RecruitType type : RecruitType.values()) {
            save.then(Commands.literal(type.key())
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(c -> save(c.getSource(), StringArgumentType.getString(c, "name"), type))));
        }
        return Commands.literal("recruit")
            .then(save)
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.word())
                    .suggests((c, sb) -> {
                        RecruitCatalog.get(c.getSource().getServer()).entries().keySet().forEach(sb::suggest);
                        return sb.buildFuture();
                    })
                    .executes(c -> delete(c.getSource(), StringArgumentType.getString(c, "name")))))
            .then(Commands.literal("list").executes(c -> list(c.getSource())));
    }

    private static int save(CommandSourceStack s, String name, RecruitType type) throws CommandSyntaxException {
        Recruits.saveAimed(s.getPlayerOrException(), name, type);
        return 1;
    }

    private static int delete(CommandSourceStack s, String name) throws CommandSyntaxException {
        Recruits.delete(s.getPlayerOrException(), name);
        return 1;
    }

    private static int list(CommandSourceStack s) {
        var entries = RecruitCatalog.get(s.getServer()).entries();
        if (entries.isEmpty()) {
            s.sendSuccess(() -> Component.literal("No recruits saved"), false);
            return 0;
        }
        entries.forEach((name, e) -> s.sendSuccess(
            () -> Component.literal(name + ": " + e.type().label + ", " + e.type().price + " gold ingots"), false));
        return entries.size();
    }
}
