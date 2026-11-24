package dev.storm.npcaddon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.faction.PlayerStandings;
import dev.storm.npcaddon.faction.Relation;
import dev.storm.npcaddon.faction.Standing;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** /npcaddon awrelation|awplayer|awfactions: relations and standings with Ancient Warfare factions. */
final class AwCommands {
    private AwCommands() {}

    private static CompletableFuture<Suggestions> suggestAw(CommandContext<CommandSourceStack> c, SuggestionsBuilder sb) {
        for (String n : AwRelations.names()) sb.suggest(n);
        return sb.buildFuture();
    }

    private static boolean missing(CommandSourceStack s) {
        if (AwRelations.available()) return false;
        s.sendFailure(Component.literal("Ancient Warfare is not installed"));
        return true;
    }

    static LiteralArgumentBuilder<CommandSourceStack> relation() {
        return Commands.literal("awrelation")
            .then(Commands.argument("faction", IntegerArgumentType.integer(0))
                .then(Commands.argument("aw", StringArgumentType.word()).suggests(AwCommands::suggestAw)
                    .executes(c -> query(c.getSource(), IntegerArgumentType.getInteger(c, "faction"),
                        StringArgumentType.getString(c, "aw")))
                    .then(Commands.argument("relation", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            for (Relation r : Relation.values()) sb.suggest(r.name().toLowerCase(Locale.ROOT));
                            return sb.buildFuture();
                        })
                        .executes(c -> apply(c.getSource(), IntegerArgumentType.getInteger(c, "faction"),
                            StringArgumentType.getString(c, "aw"), StringArgumentType.getString(c, "relation"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> player() {
        return Commands.literal("awplayer")
            .then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("aw", StringArgumentType.word()).suggests(AwCommands::suggestAw)
                    .then(Commands.argument("standing", StringArgumentType.word())
                        .suggests((c, sb) -> {
                            for (Standing st : Standing.values()) sb.suggest(st.name().toLowerCase(Locale.ROOT));
                            return sb.buildFuture();
                        })
                        .executes(c -> setPlayers(c.getSource(), EntityArgument.getPlayers(c, "targets"),
                            StringArgumentType.getString(c, "aw"), StringArgumentType.getString(c, "standing"))))));
    }

    static LiteralArgumentBuilder<CommandSourceStack> list() {
        return Commands.literal("awfactions").executes(c -> listFactions(c.getSource()));
    }

    private static int query(CommandSourceStack s, int faction, String aw) {
        if (missing(s)) return 0;
        if (!AwRelations.exists(aw)) {
            s.sendFailure(Component.literal("Unknown Ancient Warfare faction '" + aw + "' (see /npcaddon awfactions)"));
            return 0;
        }
        Relation r = AwRelations.get(s.getServer(), faction, aw);
        s.sendSuccess(() -> Component.literal("Faction " + faction + " <-> AW " + aw.toLowerCase(Locale.ROOT) + ": " + r), false);
        return 1;
    }

    private static int apply(CommandSourceStack s, int faction, String aw, String name) {
        if (missing(s)) return 0;
        Relation r;
        try {
            r = Relation.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            s.sendFailure(Component.literal("Relation must be hostile, neutral or allied"));
            return 0;
        }
        if (!AwRelations.set(s.getServer(), faction, aw, r)) {
            s.sendFailure(Component.literal("Unknown CustomNPCs faction id or Ancient Warfare faction"));
            return 0;
        }
        s.sendSuccess(() -> Component.literal("Faction " + faction + " <-> AW " + aw.toLowerCase(Locale.ROOT)
            + " set to " + r), true);
        return 1;
    }

    private static int setPlayers(CommandSourceStack s, Collection<ServerPlayer> targets, String aw, String standing) {
        if (missing(s)) return 0;
        Standing st;
        try {
            st = Standing.valueOf(standing.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            s.sendFailure(Component.literal("Standing must be hostile, neutral or friendly"));
            return 0;
        }
        int ok = 0;
        for (ServerPlayer p : targets) {
            PlayerStandings.Result r = AwRelations.setPlayer(p, aw, st);
            if (!r.ok()) {
                s.sendFailure(Component.literal(r.message()));
                return 0;
            }
            ok++;
            s.sendSuccess(() -> Component.literal(r.message()), true);
        }
        return ok;
    }

    private static int listFactions(CommandSourceStack s) {
        if (missing(s)) return 0;
        StringBuilder sb = new StringBuilder("Ancient Warfare factions (default standing toward players): ");
        boolean first = true;
        for (String n : AwRelations.names()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append(n).append(' ').append(AwRelations.defaultStanding(n));
        }
        s.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}
