package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.RaidStage;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import java.util.List;
import java.util.Optional;

/** What a survivor would tell the ruler to do next. The first matching rule wins. */
public final class Advice {
	private Advice() {}

	/** A message key, its arguments, and the event that makes the advice true. */
	public record Line(String key, List<String> args, long causeEventId) {
		public Line {
			args = List.copyOf(args);
		}
	}

	public static Optional<Line> next(SimState s, long sid, long now, int foodQuarters) {
		Settlement st = s.settlement(sid).orElse(null);
		if (st == null) return Optional.empty();
		List<Character> living = s.residents(sid);
		if (living.isEmpty()) return Optional.empty();
		long founded = st.foundedEventId();

		for (Raid raid : s.raids()) {
			if (raid.settlementId() != sid) continue;
			if (raid.stage() != RaidStage.SIGHTED && raid.stage() != RaidStage.ARRIVED) continue;
			long hours = raid.stage() == RaidStage.ARRIVED ? 0 : Math.max(0, (raid.arriveTime() - now) / GameTime.TICKS_PER_HOUR);
			long cause = Raids.latestFor(s, EventTypes.RAID_SIGHTED, Ref.raid(raid.id())).map(Event::id).orElse(raid.plannedEventId());
			return Optional.of(new Line("advice.raid", List.of(Integer.toString(raid.size()), Long.toString(hours)), cause));
		}
		if (st.granary() == null) return Optional.of(new Line("advice.granary", List.of(), founded));

		List<BuildSite> sites = s.buildSites().stream().filter(b -> b.settlementId() == sid).toList();
		Optional<BuildSite> farm = sites.stream().filter(b -> b.blueprintId().equals(Blueprint.FARM)).findFirst();
		if (farm.isEmpty()) return Optional.of(new Line("advice.farm", List.of(), founded));
		if (living.stream().noneMatch(c -> c.profession() == Profession.FARMER)) {
			return Optional.of(new Line("advice.farmer", List.of(), farm.get().placedEventId()));
		}
		Optional<BuildSite> unfinished = sites.stream().filter(b -> !b.complete()).findFirst();
		if (unfinished.isPresent() && living.stream().noneMatch(c -> c.profession() == Profession.BUILDER)) {
			return Optional.of(new Line("advice.builder", List.of(), unfinished.get().placedEventId()));
		}
		int beds = s.fact("beds." + sid).map(Integer::parseInt).orElse(0);
		if (beds < living.size()) {
			Optional<BuildSite> bunk = sites.stream().filter(b -> b.blueprintId().equals(Blueprint.BUNKHOUSE)).findFirst();
			if (bunk.isEmpty()) return Optional.of(new Line("advice.bunkhouse", List.of(), founded));
		}
		int need = ActOne.winterNeedQuarters(living.size());
		if (Calendar.season(now) != Season.WINTER && foodQuarters < need) {
			long cause = s.fact("winter_warned." + sid).map(Long::parseLong).orElse(founded);
			return Optional.of(new Line("advice.food", List.of(
					Integer.toString(foodQuarters / Balance.QUARTERS_PER_MEAL),
					Integer.toString(need / Balance.QUARTERS_PER_MEAL)), cause));
		}
		for (Character c : living) {
			if (c.condition() == Condition.WELL) continue;
			long cause = Raids.latestFor(s, EventTypes.CONDITION_CHANGED, Ref.character(c.id())).map(Event::id).orElse(founded);
			return Optional.of(new Line("advice.unwell", List.of(c.name(), c.condition().name()), cause));
		}
		return Optional.empty();
	}
}
