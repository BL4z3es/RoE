package io.github.bl4z3es.riseofempires.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Plot;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;

/** Blueprint progress: placing a ghost, building block by block, finishing. */
public final class Building {
	private Building() {}

	/** Quarter turns clockwise. One turn maps (dx, dz) to (-dz, dx). */
	public static int[] rotate(int dx, int dz, int rotation) {
		int x = dx, z = dz;
		for (int i = Math.floorMod(rotation, 4); i > 0; i--) {
			int nx = -z;
			z = x;
			x = nx;
		}
		return new int[] {x, z};
	}

	public static Place worldPos(BuildSite site, BlueprintBlock b) {
		int[] r = rotate(b.dx(), b.dz(), site.rotation());
		Place o = site.origin();
		return new Place(o.dimension(), o.x() + r[0], o.y() + b.dy(), o.z() + r[1]);
	}

	public static Event placeBlueprint(SimState s, long sid, Blueprint bp, Place origin, int rotation, String byPlayer, long now) {
		Settlement st = s.settlement(sid).orElseThrow();
		long id = s.nextId("site");
		Event e = s.ledger().append(EventDraft.of(EventTypes.BLUEPRINT_PLACED, now).at(origin)
				.actor(Ref.player(byPlayer)).actor(Ref.settlement(sid))
				.cause(st.foundedEventId())
				.effect(Ref.settlement(sid), "site", bp.id() + " (" + bp.blocks().size() + " blocks)"));
		s.put(new BuildSite(id, sid, bp.id(), origin, rotation, 0, bp.blocks().size(), e.id()), e);
		return e;
	}

	/** Records blocks built. Returns the events written: BLOCK_BUILT, plus BLUEPRINT_COMPLETED if it finished. */
	public static List<Event> blocksBuilt(SimState s, long siteId, int count, long characterId, long now,
			Map<String, Blueprint> blueprints) {
		BuildSite site = s.buildSite(siteId).orElseThrow();
		List<Event> out = new ArrayList<>();
		if (count <= 0 || site.complete()) return out;
		int placed = Math.min(site.total(), site.placed() + count);
		EventDraft d = EventDraft.of(EventTypes.BLOCK_BUILT, now).at(site.origin())
				.actor(Ref.settlement(site.settlementId()))
				.cause(site.placedEventId())
				.effect(Ref.settlement(site.settlementId()), "built", placed + "/" + site.total());
		if (characterId > 0) d.actor(Ref.character(characterId));
		Event built = s.ledger().append(d);
		BuildSite next = site.withPlaced(placed);
		s.put(next, built);
		out.add(built);
		if (!next.complete()) return out;

		Blueprint bp = blueprints.get(site.blueprintId());
		if (bp == null) throw new IllegalArgumentException("Unknown blueprint " + site.blueprintId());
		List<Plot> plots = new ArrayList<>(s.farm(site.settlementId()));
		int plotsAdded = 0, beds = 0;
		for (BlueprintBlock b : bp.blocks()) {
			if (b.blockState().startsWith("minecraft:wheat")) {
				plots.add(new Plot(worldPos(site, b), "minecraft:wheat", 0));
				plotsAdded++;
			}
			if (b.blockState().contains("_bed[") && b.blockState().contains("part=head")) beds++;
		}
		Ref sref = Ref.settlement(site.settlementId());
		EventDraft cd = EventDraft.of(EventTypes.BLUEPRINT_COMPLETED, now).at(site.origin())
				.actor(sref).cause(built.id())
				.effect(sref, "completed", bp.id());
		if (plotsAdded > 0) cd.effect(sref, "plots", "+" + plotsAdded);
		if (beds > 0) cd.effect(sref, "beds", "+" + beds);
		Event done = s.ledger().append(cd);
		if (plotsAdded > 0) s.putFarm(site.settlementId(), plots, done);
		if (beds > 0) {
			String key = "beds." + site.settlementId();
			int have = Integer.parseInt(s.fact(key).orElse("0"));
			s.putFact(key, Integer.toString(have + beds), done);
		}
		out.add(done);
		return out;
	}

	/** The incomplete site with the lowest id. */
	public static Optional<BuildSite> activeSite(SimState s, long sid) {
		for (BuildSite b : s.buildSites()) {
			if (b.settlementId() == sid && !b.complete()) return Optional.of(b);
		}
		return Optional.empty();
	}
}
