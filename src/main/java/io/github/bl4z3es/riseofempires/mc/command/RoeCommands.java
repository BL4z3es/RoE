package io.github.bl4z3es.riseofempires.mc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.Stockpile;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Standing;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** {@code /roe} debug commands (rule E26). Each shows the state of one system; none changes anything. */
public final class RoeCommands {
	private RoeCommands() {}

	/** Hook so the stockpile module can recount containers before {@code /roe stockpile} reports. */
	public static Consumer<Settlement> beforeStockpileReport = s -> {};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("roe")
				.then(Commands.literal("time").executes(c -> run(c, RoeCommands::time)))
				.then(Commands.literal("events")
						.executes(c -> run(c, (src, roe) -> events(src, roe, 10)))
						.then(Commands.argument("n", IntegerArgumentType.integer(1, 100))
								.executes(c -> run(c, (src, roe) -> events(src, roe, IntegerArgumentType.getInteger(c, "n"))))))
				.then(Commands.literal("why")
						.then(Commands.argument("eventId", LongArgumentType.longArg(1))
								.executes(c -> run(c, (src, roe) -> why(src, roe, LongArgumentType.getLong(c, "eventId"))))))
				.then(Commands.literal("settlement").executes(c -> run(c, RoeCommands::settlement)))
				.then(Commands.literal("stockpile").executes(c -> run(c, RoeCommands::stockpile)))
				.then(Commands.literal("standing").executes(c -> run(c, RoeCommands::standing))));
	}

	private interface Body { void run(CommandSourceStack src, RoeServer roe); }

	private static int run(CommandContext<CommandSourceStack> c, Body body) {
		Optional<RoeServer> roe = RoeServer.get();
		if (roe.isEmpty()) {
			c.getSource().sendFailure(Component.literal("Rise of Empires is not running on this server yet."));
			return 0;
		}
		body.run(c.getSource(), roe.get());
		return 1;
	}

	private static void say(CommandSourceStack src, String text) {
		src.sendSuccess(() -> Component.literal(text), false);
	}

	private static void time(CommandSourceStack src, RoeServer roe) {
		long now = roe.now();
		say(src, "It is " + Describe.when(now) + " (world time " + now + ").");
	}

	private static void events(CommandSourceStack src, RoeServer roe, int n) {
		SimState s = roe.state();
		List<Event> latest = s.ledger().latest(n);
		if (latest.isEmpty()) { say(src, "The Ledger is empty."); return; }
		say(src, "Newest " + latest.size() + " of " + s.ledger().size() + " events:");
		for (Event e : latest) say(src, Describe.line(s, e));
	}

	private static void why(CommandSourceStack src, RoeServer roe, long id) {
		SimState s = roe.state();
		Optional<Event> e = s.ledger().get(id);
		if (e.isEmpty()) { src.sendFailure(Component.literal("No event #" + id + ".")); return; }
		say(src, Describe.full(s, e.get()));
		List<Event> chain = s.ledger().causeChain(id);
		if (chain.isEmpty()) { say(src, "Nothing caused it: it is where a chain begins."); return; }
		say(src, "It happened because of:");
		for (Event c : chain) say(src, "  " + Describe.line(s, c));
	}

	private static Optional<Settlement> here(CommandSourceStack src, RoeServer roe) {
		BlockPos pos = BlockPos.containing(src.getPosition());
		Place p = RoeServer.place(src.getLevel(), pos);
		Optional<Settlement> s = Founding.settlementAt(roe.state(), p);
		if (s.isEmpty()) src.sendFailure(Component.literal("You are not standing in any settlement's claim."));
		return s;
	}

	private static void settlement(CommandSourceStack src, RoeServer roe) {
		SimState s = roe.state();
		here(src, roe).ifPresent(st -> {
			say(src, st.name() + " (settlement " + st.id() + "), centre " + st.centre() + ", claim radius " + st.claimRadius()
					+ ", founded " + Describe.when(s.ledger().get(st.foundedEventId()).map(Event::time).orElse(0L)) + " (#" + st.foundedEventId() + ")");
			say(src, "Granary: " + (st.granary() == null ? "none" : st.granary().toString()) + ". Act I: " + s.actOne(st.id()) + ".");
			List<Character> people = s.residents(st.id());
			say(src, people.size() + " living residents:");
			for (Character c : people) {
				say(src, "  " + c.name() + " - " + c.profession().name().toLowerCase() + ", " + c.condition().name().toLowerCase()
						+ ", missed meals " + c.needs().missedMeals() + ", rough nights " + c.needs().nightsRough() + "; " + c.trait() + "; wants " + c.want());
			}
		});
	}

	private static void stockpile(CommandSourceStack src, RoeServer roe) {
		SimState s = roe.state();
		here(src, roe).ifPresent(st -> {
			beforeStockpileReport.accept(st);
			Stockpile sp = s.stockpile(st.id());
			Map<String, Integer> totals = sp.totals();
			if (st.granary() == null) say(src, st.name() + " has no Granary Seal, so nothing is counted as its store.");
			say(src, st.name() + " store: " + (totals.isEmpty() ? "empty" : "") + " (food " + roe.content().food().totalQuarters(sp) / 4.0 + " meals)");
			for (Map.Entry<String, Integer> e : totals.entrySet()) {
				int pending = sp.pending().getOrDefault(e.getKey(), 0);
				say(src, "  " + e.getKey() + " x" + e.getValue() + (pending != 0 ? " (" + (pending > 0 ? "+" : "") + pending + " not yet in the chests)" : ""));
			}
			say(src, "Last change: #" + s.lastStockEvent(st.id()));
		});
	}

	private static void standing(CommandSourceStack src, RoeServer roe) {
		here(src, roe).ifPresent(st -> {
			Standing.Parts p = Standing.of(roe.state(), st.id());
			say(src, String.format("%s Standing %.2f = population %.2f (%d people) + territory %.2f (%d chunks)",
					st.name(), p.total(), p.populationTerm(), p.population(), p.territoryTerm(), p.territoryChunks()));
			say(src, "Season: " + Calendar.season(roe.now()).name().toLowerCase() + ".");
		});
	}
}
