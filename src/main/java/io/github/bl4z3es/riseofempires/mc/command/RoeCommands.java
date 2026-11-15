package io.github.bl4z3es.riseofempires.mc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.WorldState;
import io.github.bl4z3es.riseofempires.core.sim.camp.Food;
import io.github.bl4z3es.riseofempires.core.sim.camp.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Needs;
import io.github.bl4z3es.riseofempires.core.sim.camp.Standing;
import io.github.bl4z3es.riseofempires.mc.block.ModItems;
import io.github.bl4z3es.riseofempires.mc.world.RoeWorld;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The {@code /roe} debug commands (rule E26). Read-only ones need no permission. */
public final class RoeCommands {
    private RoeCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("roe")
                .then(Commands.literal("time").executes(RoeCommands::time))
                .then(Commands.literal("events").executes(c -> events(c, 10))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 200))
                                .executes(c -> events(c, IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("why").then(Commands.argument("eventId", LongArgumentType.longArg(1))
                        .executes(c -> why(c, LongArgumentType.getLong(c, "eventId")))))
                .then(Commands.literal("settlement").executes(RoeCommands::settlement))
                .then(Commands.literal("stockpile").executes(RoeCommands::stockpile))
                .then(Commands.literal("standing").executes(RoeCommands::standing))
                .then(Commands.literal("stats").then(Commands.argument("ref", StringArgumentType.greedyString())
                        .executes(c -> stats(c, StringArgumentType.getString(c, "ref")))))
                .then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("charter").executes(RoeCommands::giveCharter))));
    }

    private static RoeWorld world(CommandContext<CommandSourceStack> c) {
        RoeWorld w = RoeWorld.get(c.getSource().getServer());
        if (w == null) throw new IllegalStateException("Rise of Empires is not running on this server");
        return w;
    }

    private static void say(CommandContext<CommandSourceStack> c, String text) {
        c.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static void say(CommandContext<CommandSourceStack> c, String text, ChatFormatting style) {
        c.getSource().sendSuccess(() -> Component.literal(text).withStyle(style), false);
    }

    private static String when(long hour) {
        return "day " + (Calendar.day(hour) + 1) + " " + String.format(Locale.ROOT, "%02d:00", (Calendar.hourOfDay(hour) + 6) % 24);
    }

    private static int time(CommandContext<CommandSourceStack> c) {
        RoeWorld w = world(c);
        long h = w.state().hour();
        say(c, "It is " + when(h) + ", " + Calendar.season(h).name().toLowerCase(Locale.ROOT) + " of year "
                + (Calendar.year(h) + 1) + ". " + Calendar.daysUntilNextSeason(h) + " days until "
                + Calendar.next(Calendar.season(h)).name().toLowerCase(Locale.ROOT) + "."
                + (w.caughtUp() ? "" : " (The simulation is still catching up.)"));
        return 1;
    }

    private static int events(CommandContext<CommandSourceStack> c, int n) {
        List<Event> list = world(c).sim().ledger().recent(n);
        if (list.isEmpty()) say(c, "Nothing has happened yet.");
        for (Event e : list) say(c, "#" + e.id() + "  " + when(e.hour()) + "  " + e.summary());
        return list.size();
    }

    private static int why(CommandContext<CommandSourceStack> c, long id) {
        var ledger = world(c).sim().ledger();
        Event e = ledger.get(id);
        if (e == null) {
            c.getSource().sendFailure(Component.literal("There is no event #" + id + "."));
            return 0;
        }
        say(c, "#" + e.id() + "  " + when(e.hour()) + "  " + e.summary(), ChatFormatting.GOLD);
        if (e.causes().isEmpty()) {
            say(c, "  No cause was recorded: this began here.", ChatFormatting.GRAY);
            return 1;
        }
        printCauses(c, ledger, e, 1, new java.util.HashSet<>());
        return 1;
    }

    private static void printCauses(CommandContext<CommandSourceStack> c, io.github.bl4z3es.riseofempires.core.ledger.Ledger ledger, Event e,
                                    int depth, java.util.Set<Long> seen) {
        for (long id : e.causes()) {
            Event cause = ledger.get(id);
            String indent = "  ".repeat(depth) + "<- ";
            if (!seen.add(id)) {
                say(c, indent + "#" + id + " (see above)", ChatFormatting.DARK_GRAY);
                continue;
            }
            say(c, indent + "#" + cause.id() + "  " + when(cause.hour()) + "  " + cause.summary(), ChatFormatting.GRAY);
            printCauses(c, ledger, cause, depth + 1, seen);
        }
    }

    private static Settlement here(CommandContext<CommandSourceStack> c) {
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        return Founding.settlementAt(world(c).state(), RoeWorld.place(c.getSource().getLevel(), pos));
    }

    private static int settlement(CommandContext<CommandSourceStack> c) {
        Settlement s = here(c);
        if (s == null) {
            say(c, "You are not standing in any settlement's claim.");
            return 0;
        }
        WorldState state = world(c).state();
        var nation = state.nation(s.nationId());
        say(c, s.name() + " (" + s.rank() + ") of " + (nation == null ? "no nation" : nation.name()), ChatFormatting.GOLD);
        say(c, "Centre " + s.centre().x() + " " + s.centre().y() + " " + s.centre().z() + ", claim radius " + s.claimRadius()
                + ", " + (state.embodied(s.id()) ? "watched (embodied)" : "unwatched (ledger tier)"));
        say(c, "Beds: " + (int) state.stat(Ref.settlement(s.id()), Needs.BEDS) + ". Meals in store: "
                + (int) Food.meals(state.stockpile(s.stockpileId())) + ".");
        List<Character> people = state.residents(s.id());
        if (people.isEmpty()) say(c, "Nobody lives here.");
        for (Character p : people) {
            Ref r = Ref.character(p.id());
            String condition = state.tag(r, Needs.CONDITION);
            String idle = state.tag(r, Jobs.IDLE_REASON);
            say(c, "  " + p.name() + ", " + (p.profession().isEmpty() ? "no work" : p.profession()) + ", " + p.trait()
                    + (condition.isEmpty() ? "" : ", " + condition) + (idle.isEmpty() ? "" : " (idle: " + idle + ")"));
        }
        return people.size();
    }

    private static int stockpile(CommandContext<CommandSourceStack> c) {
        Settlement s = here(c);
        if (s == null) {
            say(c, "You are not standing in any settlement's claim.");
            return 0;
        }
        Stockpile stock = world(c).state().stockpile(s.stockpileId());
        say(c, "The stockpile of " + s.name() + ":", ChatFormatting.GOLD);
        if (stock.items().isEmpty()) say(c, "  empty");
        for (Map.Entry<String, Integer> e : stock.items().entrySet()) say(c, "  " + e.getValue() + " " + Food.itemName(e.getKey()));
        return stock.items().size();
    }

    private static int standing(CommandContext<CommandSourceStack> c) {
        Settlement s = here(c);
        if (s == null) {
            say(c, "You are not standing in any settlement's claim.");
            return 0;
        }
        Standing st = Standing.of(world(c).state(), s.nationId());
        say(c, String.format(Locale.ROOT, "Standing %.2f = population %.2f (%d people, weight %.2f) + territory %.2f (%.1f chunks, weight %.2f)",
                st.score(), st.populationPart(), st.population(), Balance.STANDING_POPULATION_WEIGHT,
                st.territoryPart(), st.territoryChunks(), Balance.STANDING_TERRITORY_WEIGHT));
        return (int) Math.round(st.score() * 100);
    }

    private static int stats(CommandContext<CommandSourceStack> c, String text) {
        Ref ref;
        try {
            ref = Ref.parse(text.strip());
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal("Write it as kind:id, for example character:character-12-1"));
            return 0;
        }
        WorldState state = world(c).state();
        say(c, ref.toString(), ChatFormatting.GOLD);
        state.stats(ref).forEach((k, v) -> say(c, "  " + k + " = " + v));
        state.tags(ref).forEach((k, v) -> say(c, "  " + k + " = \"" + v + "\""));
        return 1;
    }

    private static int giveCharter(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        c.getSource().getPlayerOrException().getInventory().add(new ItemStack(ModItems.CHARTER));
        say(c, "You hold a Charter. Place it on a lectern to found a camp.");
        return 1;
    }
}
