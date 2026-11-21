package io.github.bl4z3es.riseofempires.mc.blueprint;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;

/**
 * The ghost outline: once a second, every unfinished building within sight range of a player gets its
 * footprint box drawn in particles, with a marker on the next block to be built. It shows what the
 * builders will do and where they are up to. Nothing here changes the world.
 */
public final class BlueprintOutline implements RoeServer.Module {
	/** How close a player must be to see an outline, in blocks. */
	public static final double VIEW_DISTANCE = 64.0; // PROPOSED: not in Balance yet

	private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

	private final Map<Long, Box> boxes = new HashMap<>();

	@Override
	public void tick(RoeServer roe, long now) {
		boxes.keySet().removeIf(id -> roe.state().buildSite(id).map(BuildSite::complete).orElse(true));
		for (BuildSite site : roe.state().buildSites()) {
			if (site.complete()) continue;
			Blueprint bp = roe.content().blueprints().get(site.blueprintId());
			if (bp == null || bp.blocks().isEmpty()) continue;
			ServerLevel level = roe.level(site.origin());
			Place o = site.origin();
			if (level.getNearestPlayer(o.x() + 0.5, o.y() + 0.5, o.z() + 0.5, VIEW_DISTANCE, false) == null) continue;
			Box box = boxes.computeIfAbsent(site.id(), id -> boxOf(site, bp));
			drawBox(level, box);
			if (site.placed() < bp.blocks().size()) {
				Place next = Building.worldPos(site, bp.blocks().get(site.placed()));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, next.x() + 0.5, next.y() + 0.5, next.z() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
			}
		}
	}

	private static Box boxOf(BuildSite site, Blueprint bp) {
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlueprintBlock b : bp.blocks()) {
			Place p = Building.worldPos(site, b);
			minX = Math.min(minX, p.x());
			minY = Math.min(minY, p.y());
			minZ = Math.min(minZ, p.z());
			maxX = Math.max(maxX, p.x());
			maxY = Math.max(maxY, p.y());
			maxZ = Math.max(maxZ, p.z());
		}
		return new Box(minX, minY, minZ, maxX, maxY, maxZ);
	}

	/** The twelve edges, one particle per block along each. */
	private static void drawBox(ServerLevel level, Box b) {
		double x0 = b.minX(), x1 = b.maxX() + 1.0, y0 = b.minY(), y1 = b.maxY() + 1.0, z0 = b.minZ(), z1 = b.maxZ() + 1.0;
		for (double x = x0; x <= x1; x += 1.0) {
			dot(level, x, y0, z0);
			dot(level, x, y0, z1);
			dot(level, x, y1, z0);
			dot(level, x, y1, z1);
		}
		for (double z = z0; z <= z1; z += 1.0) {
			dot(level, x0, y0, z);
			dot(level, x1, y0, z);
			dot(level, x0, y1, z);
			dot(level, x1, y1, z);
		}
		for (double y = y0; y <= y1; y += 1.0) {
			dot(level, x0, y, z0);
			dot(level, x1, y, z0);
			dot(level, x0, y, z1);
			dot(level, x1, y, z1);
		}
	}

	private static void dot(ServerLevel level, double x, double y, double z) {
		level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
	}
}
