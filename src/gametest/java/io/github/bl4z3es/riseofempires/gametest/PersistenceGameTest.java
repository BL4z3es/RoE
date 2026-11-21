package io.github.bl4z3es.riseofempires.gametest;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.model.StateCodec;
import io.github.bl4z3es.riseofempires.core.sim.Founding;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** Task M1-02: save the world, read it back from disk, find identical state. */
public final class PersistenceGameTest {
	@GameTest
	public void savesAndReloadsIdenticalState(GameTestHelper helper) throws Exception {
		RoeServer roe = RoeServer.get().orElseThrow();
		SimState s = roe.state();
		long now = roe.now();
		String player = UUID.randomUUID().toString();
		Place centre = RoeServer.place(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
		Founding.found(s, player, "Testhold", centre, now).orElseThrow();
		long sid = s.settlementFoundedBy(player).orElseThrow();
		List<Survivors.Template> pool = List.of(
				new Survivors.Template("Ada", "steady", "a roof", "the fire at Elmford"),
				new Survivors.Template("Bram", "quick", "his brother", "the long road"),
				new Survivors.Template("Cora", "proud", "land", "the old empire"));
		Survivors.setOut(s, sid, now, pool);
		for (long id : Survivors.pending(s, sid)) Survivors.join(s, id, sid, now, centre);
		Jobs.assign(s, s.residents(sid).get(0).id(), Profession.FARMER, player, now);

		byte[] before = bytes(s);
		long last = s.ledger().lastId();
		SimState back = roe.saveAndReadBack();

		helper.assertTrue(back.ledger().size() == s.ledger().size(), "event count differs after reload");
		helper.assertTrue(back.ledger().all().equals(s.ledger().all()), "events differ after reload");
		helper.assertTrue(Arrays.equals(before, bytes(back)), "records differ after reload");
		helper.assertTrue(back.ledger().causeChain(last).equals(s.ledger().causeChain(last)), "cause chain differs after reload");
		helper.assertTrue(back.residents(sid).size() == 3 && back.residents(sid).get(0).profession() == Profession.FARMER,
				"characters differ after reload");
		helper.succeed();
	}

	private static byte[] bytes(SimState s) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StateCodec.write(out, s);
		return out.toByteArray();
	}
}
