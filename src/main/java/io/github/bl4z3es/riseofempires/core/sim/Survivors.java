package io.github.bl4z3es.riseofempires.core.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Condition;
import io.github.bl4z3es.riseofempires.core.model.Memory;
import io.github.bl4z3es.riseofempires.core.model.Needs;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;

/** The first survivors: they set out at dusk and join the camp when they arrive. */
public final class Survivors {
	private Survivors() {}

	/** A survivor who may be chosen. */
	public record Template(String name, String trait, String want, String memory) {}

	/** The first world time strictly after {@code foundedTime} at the set-out time of day. */
	public static long setOutTime(long foundedTime) {
		long dayStart = Math.floorDiv(foundedTime, GameTime.TICKS_PER_DAY) * GameTime.TICKS_PER_DAY;
		long t = dayStart + Balance.SURVIVORS_SET_OUT_TIME_OF_DAY;
		return t > foundedTime ? t : t + GameTime.TICKS_PER_DAY;
	}

	/** Chooses the survivors from the pool and sends them walking. */
	public static Event setOut(SimState s, long settlementId, long now, List<Template> pool) {
		int n = Balance.STARTING_SURVIVORS;
		if (pool.size() < n) {
			throw new IllegalArgumentException("Survivor pool has " + pool.size() + " templates, need " + n);
		}
		Settlement st = s.settlement(settlementId).orElseThrow();
		List<Template> shuffled = new ArrayList<>(pool);
		Collections.shuffle(shuffled, Dice.of(s.seed(), settlementId, 11));
		List<Character> chosen = new ArrayList<>();
		for (Template t : shuffled.subList(0, n)) {
			chosen.add(new Character(s.nextId("character"), t.name(), t.trait(), t.want(), Profession.NONE,
					List.of(new Memory(0, t.memory())), Needs.MET, Condition.WELL, 0));
		}
		EventDraft d = EventDraft.of(EventTypes.SURVIVORS_SET_OUT, now).at(st.centre())
				.cause(st.foundedEventId());
		StringBuilder ids = new StringBuilder();
		for (Character c : chosen) {
			d.actor(Ref.character(c.id()));
			d.effect(Ref.character(c.id()), "set out for", st.name());
			if (ids.length() > 0) ids.append(',');
			ids.append(c.id());
		}
		Event e = s.ledger().append(d);
		for (Character c : chosen) s.put(c, e);
		s.putFact("survivors." + settlementId, ids.toString(), e);
		return e;
	}

	/** A survivor reaches the camp and joins its population. */
	public static Event join(SimState s, long characterId, long settlementId, long now, Place where) {
		Character c = s.character(characterId).orElseThrow();
		Settlement st = s.settlement(settlementId).orElseThrow();
		String me = Long.toString(characterId);
		EventDraft d = EventDraft.of(EventTypes.SURVIVOR_JOINED, now).at(where)
				.actor(Ref.character(characterId)).actor(Ref.settlement(settlementId))
				.effect(Ref.settlement(settlementId), "resident", c.name());
		for (Event e : s.ledger().all()) {
			if (e.type().equals(EventTypes.SURVIVORS_SET_OUT)
					&& e.actors().stream().anyMatch(a -> a.kind().equals(Ref.CHARACTER) && a.id().equals(me))) {
				d.cause(e.id());
				break;
			}
		}
		Event e = s.ledger().append(d);
		s.put(c.withSettlement(settlementId), e);
		s.put(st.withResident(characterId), e);
		return e;
	}

	/** Survivors who set out but have not joined yet. */
	public static List<Long> pending(SimState s, long settlementId) {
		List<Long> out = new ArrayList<>();
		String ids = s.fact("survivors." + settlementId).orElse("");
		if (ids.isEmpty()) return out;
		for (String part : ids.split(",")) {
			long id = Long.parseLong(part);
			s.character(id).filter(c -> c.settlementId() == 0 && !c.condition().isGone()).ifPresent(c -> out.add(id));
		}
		return out;
	}
}
