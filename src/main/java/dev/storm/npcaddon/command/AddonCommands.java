package dev.storm.npcaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.storm.npcaddon.faction.FactionRelations;
import dev.storm.npcaddon.faction.PlayerStandings;
import dev.storm.npcaddon.faction.Relation;
import dev.storm.npcaddon.faction.Standing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = "npcaddon")
public final class AddonCommands {
    private AddonCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("npcaddon").requires(s -> s.hasPermission(2))
            .then(GroupCommands.build())
            .then(Commands.literal("player")
                .then(Commands.argument("targets", EntityArgument.players())
                    .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                        .then(Commands.argument("standing", StringArgumentType.word())
                            .suggests(AddonCommands::suggestStandings)
                            .executes(ctx -> playerStanding(ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets"),
                                IntegerArgumentType.getInteger(ctx, "faction"),
                                StringArgumentType.getString(ctx, "standing")))))))
            .then(Commands.literal("default")
                .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                    .then(Commands.argument("standing", StringArgumentType.word())
                        .suggests(AddonCommands::suggestStandings)
                        .executes(ctx -> factionDefault(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "faction"),
                            StringArgumentType.getString(ctx, "standing"), false))
                        .then(Commands.literal("apply")
                            .executes(ctx -> factionDefault(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "faction"),
                                StringArgumentType.getString(ctx, "standing"), true))))))
            .then(Commands.literal("relation")
                .then(Commands.argument("a", IntegerArgumentType.integer(0))
                    .then(Commands.argument("b", IntegerArgumentType.integer(0))
                        .executes(ctx -> query(ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "b")))
                        .then(Commands.argument("relation", StringArgumentType.word())
                            .suggests((c, sb) -> {
                                for (Relation r : Relation.values()) sb.suggest(r.name().toLowerCase(Locale.ROOT));
                                return sb.buildFuture();
                            })
                            .executes(ctx -> apply(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "a"), IntegerArgumentType.getInteger(ctx, "b"),
                                StringArgumentType.getString(ctx, "relation"))))))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestStandings(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> c,
            com.mojang.brigadier.suggestion.SuggestionsBuilder sb) {
        for (Standing s : Standing.values()) sb.suggest(s.name().toLowerCase(Locale.ROOT));
        return sb.buildFuture();
    }

    private static Standing parseStanding(CommandSourceStack src, String name) {
        try {
            return Standing.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Standing must be hostile, neutral or friendly"));
            return null;
        }
    }

    private static int playerStanding(CommandSourceStack src, java.util.Collection<ServerPlayer> targets,
                                      int faction, String standing) {
        Standing s = parseStanding(src, standing);
        if (s == null) return 0;
        int ok = 0;
        for (ServerPlayer p : targets) {
            PlayerStandings.Result r = PlayerStandings.setPlayer(p, faction, s);
            if (r.ok()) {
                ok++;
                src.sendSuccess(() -> Component.literal(r.message()), true);
            } else {
                src.sendFailure(Component.literal(r.message()));
                return 0;
            }
        }
        return ok;
    }

    private static int factionDefault(CommandSourceStack src, int faction, String standing, boolean apply) {
        Standing s = parseStanding(src, standing);
        if (s == null) return 0;
        PlayerStandings.Result r = PlayerStandings.setDefault(src.getServer(), faction, s, apply);
        if (!r.ok()) {
            src.sendFailure(Component.literal(r.message()));
            return 0;
        }
        src.sendSuccess(() -> Component.literal(r.message()), true);
        return 1;
    }

    private static int query(CommandSourceStack src, int a, int b) {
        Relation r = FactionRelations.get(src.getServer(), a, b);
        src.sendSuccess(() -> Component.literal("Faction " + a + " <-> " + b + ": " + r), false);
        return 1;
    }

    private static int apply(CommandSourceStack src, int a, int b, String name) {
        Relation r;
        try {
            r = Relation.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.literal("Relation must be hostile, neutral or allied"));
            return 0;
        }
        if (!FactionRelations.set(src.getServer(), a, b, r)) {
            src.sendFailure(Component.literal("Unknown faction id, or both ids are the same"));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Faction " + a + " <-> " + b + " set to " + r), true);
        return 1;
    }
}
