package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Keeps each body's job in step with its record's profession (task M1-14). Runs once a second.
 * Jobs that are not work jobs (the walk-in of a new arrival, a test's own job) are left alone.
 */
public final class JobModule implements RoeServer.Module {
	public static final JobModule INSTANCE = new JobModule();
	/** Extra search distance around a claim when looking for bodies. */
	private static final int SEARCH_MARGIN_BLOCKS = 128;

	private JobModule() {}

	public static void register() {
		RoeServer.addModule(INSTANCE);
	}

	@Override
	public void tick(RoeServer roe, long now) {
		for (Settlement st : new ArrayList<>(roe.state().settlements())) {
			if (!roe.isEmbodied(st.centre())) continue;
			ServerLevel level = roe.level(st.centre());
			BlockPos c = RoeServer.pos(st.centre());
			int r = st.claimRadius() + SEARCH_MARGIN_BLOCKS;
			AABB box = new AABB(c.getX() - r, level.getMinY(), c.getZ() - r, c.getX() + r, level.getMaxY(), c.getZ() + r);
			for (CharacterEntity body : level.getEntitiesOfClass(CharacterEntity.class, box)) sync(roe, body);
		}
	}

	/** Gives the body the job its record's profession calls for. */
	public static void sync(RoeServer roe, CharacterEntity body) {
		Optional<Character> rec = roe.state().character(body.characterId());
		if (rec.isEmpty()) return;
		Character c = rec.get();
		boolean works = c.settlementId() != 0 && !c.condition().isGone() && c.profession() != Profession.NONE;
		CharacterEntity.Job current = body.job();
		if (!works) {
			if (current instanceof WorkJob) body.setJob(null);
			return;
		}
		if (current instanceof WorkJob w && w.profession() == c.profession()) return;
		if (current != null && !(current instanceof WorkJob)) return;
		body.stopWalking();
		body.setJob(switch (c.profession()) {
			case FARMER -> new FarmerJob();
			case BUILDER -> new BuilderJob();
			case GATHERER -> new GathererJob();
			case NONE -> null;
		});
	}
}
