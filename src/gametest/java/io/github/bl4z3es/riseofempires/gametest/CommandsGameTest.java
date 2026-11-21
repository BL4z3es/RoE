package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Stores;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The {@code /roe} debug commands, run in-game against a real settlement (tasks M1-04, M1-06, M1-19):
 * {@code /roe why} prints a cause chain, {@code /roe settlement} shows the settlement you stand in,
 * {@code /roe standing} shows the score and its parts.
 */
public final class CommandsGameTest {
	@GameTest
	public void debugCommandsShowRealState(GameTestHelper helper) {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		// Far from other tests' camps so the claims do not overlap.
		BlockPos centrePos = new BlockPos(3_000_000, 64, 3_000_000);
		Place centre = new Place("minecraft:overworld", centrePos.getX(), centrePos.getY(), centrePos.getZ());
		Founding.found(s, player, "Commandhold", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		Survivors.setOut(s, sid, now, List.of(
				new Survivors.Template("Ada Fen", "steady", "a roof", "a fire"),
				new Survivors.Template("Bram Holt", "quick", "his brother", "a road"),
				new Survivors.Template("Cora Vale", "proud", "land", "an empire")));
		for (long id : Survivors.pending(s, sid)) Survivors.join(s, id, sid, now, centre);
		Event joined = s.ledger().latestOfType(EventTypes.SURVIVOR_JOINED).orElseThrow();
		Stores.sealGranary(s, sid, centre, player, now);
		Stores.recount(s, sid, Map.of("minecraft:wheat", 7), false, now);

		List<String> out = new ArrayList<>();
		CommandSourceStack source = roe.server().createCommandSourceStack()
				.withSource(capture(out))
				.withLevel(helper.getLevel())
				.withPosition(Vec3.atCenterOf(centrePos));

		run(roe, source, "roe why " + joined.id());
		String why = String.join("\n", out);
		helper.assertTrue(why.contains("survivor_joined"), "why should describe the event:\n" + why);
		helper.assertTrue(why.contains("It happened because of"), "why should list causes:\n" + why);
		helper.assertTrue(why.contains("survivors_set_out") && why.contains("settlement_founded"),
				"why should walk back to the founding:\n" + why);

		out.clear();
		run(roe, source, "roe settlement");
		String settlement = String.join("\n", out);
		helper.assertTrue(settlement.contains("Commandhold") && settlement.contains("3 living residents")
				&& settlement.contains("Ada Fen"), "settlement should show where we stand:\n" + settlement);

		out.clear();
		run(roe, source, "roe standing");
		String standing = String.join("\n", out);
		helper.assertTrue(standing.contains("Standing") && standing.contains("3 people") && standing.contains("chunks"),
				"standing should show its parts:\n" + standing);

		out.clear();
		run(roe, source, "roe time");
		helper.assertTrue(String.join("\n", out).contains("year"), "time should show the calendar");
		helper.succeed();
	}

	private static void run(RoeServer roe, CommandSourceStack source, String command) {
		roe.server().getCommands().performPrefixedCommand(source, command);
	}

	private static CommandSource capture(List<String> out) {
		return new CommandSource() {
			@Override public void sendSystemMessage(Component message) { out.add(message.getString()); }
			@Override public boolean acceptsSuccess() { return true; }
			@Override public boolean acceptsFailure() { return true; }
			@Override public boolean shouldInformAdmins() { return false; }
		};
	}
}
