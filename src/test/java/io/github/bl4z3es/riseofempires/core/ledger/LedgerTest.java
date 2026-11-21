package io.github.bl4z3es.riseofempires.core.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LedgerTest {
	/** 1 root; 2 and 3 from 1; 4 from 2 and 3; 5 from 4; 6 a second root; 7 from 6. */
	private static Ledger branching() {
		Ledger l = new Ledger();
		l.append(EventDraft.of(EventTypes.WORLD_BEGAN, 0).actor(Ref.world()));
		l.append(EventDraft.of(EventTypes.SETTLEMENT_FOUNDED, 10).at(new Place("minecraft:overworld", 1, 64, -2))
				.actor(Ref.settlement(1)).cause(1).effect(Ref.settlement(1), "name", "Ashfall é中")
				.witness(Ref.player("abc")));
		l.append(EventDraft.of(EventTypes.CHARACTER_CREATED, 11).actor(Ref.character(1)).cause(1));
		l.append(EventDraft.of(EventTypes.SURVIVOR_JOINED, 12).cause(2).cause(3)
				.effect(Ref.character(1), "settlement", "1"));
		l.append(EventDraft.of(EventTypes.JOB_ASSIGNED, 13).cause(4));
		l.append(EventDraft.of(EventTypes.RUIN_PLACED, 14));
		l.append(EventDraft.of(EventTypes.CHARTER_TAKEN, 15).cause(6));
		return l;
	}

	private static byte[] bytes(Ledger l) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		LedgerCodec.writeAll(out, l);
		return out.toByteArray();
	}

	private static Ledger load(byte[] data) throws IOException {
		return LedgerCodec.readAll(new ByteArrayInputStream(data));
	}

	private static Set<Long> ids(List<Event> events) {
		Set<Long> out = new HashSet<>();
		for (Event e : events) out.add(e.id());
		return out;
	}

	@Test
	void roundTripKeepsEveryEventAndChainsAreCorrect() throws IOException {
		Ledger original = branching();
		Ledger back = load(bytes(original));
		assertEquals(original.size(), back.size());
		for (Event e : original.all()) assertEquals(e, back.get(e.id()).orElseThrow());

		Map<Long, Set<Long>> expectedRoots = Map.of(
				1L, Set.of(1L), 2L, Set.of(1L), 3L, Set.of(1L), 4L, Set.of(1L), 5L, Set.of(1L),
				6L, Set.of(6L), 7L, Set.of(6L));
		Map<Long, Set<Long>> expectedAncestors = Map.of(
				1L, Set.of(), 2L, Set.of(1L), 3L, Set.of(1L), 4L, Set.of(1L, 2L, 3L),
				5L, Set.of(1L, 2L, 3L, 4L), 6L, Set.of(), 7L, Set.of(6L));
		for (Event e : back.all()) {
			long id = e.id();
			assertEquals(expectedRoots.get(id), ids(back.roots(id)), "roots of " + id);
			List<Event> chain = back.causeChain(id);
			assertEquals(expectedAncestors.get(id), ids(chain), "ancestors of " + id);
			assertEquals(chain.size(), ids(chain).size(), "each ancestor once for " + id);
			assertFalse(ids(chain).contains(id), "chain of " + id + " contains itself");
		}
	}

	@Test
	void nullPlaceAndPlaceSurviveRoundTrip() throws IOException {
		Ledger back = load(bytes(branching()));
		assertEquals(null, back.get(1).orElseThrow().place());
		assertEquals(new Place("minecraft:overworld", 1, 64, -2), back.get(2).orElseThrow().place());
	}

	@Test
	void badCausesAreRefused() {
		Ledger l = new Ledger();
		l.append(EventDraft.of(EventTypes.WORLD_BEGAN, 0));
		assertThrows(IllegalArgumentException.class, () -> l.append(EventDraft.of("x", 1).cause(0)));
		assertThrows(IllegalArgumentException.class, () -> l.append(EventDraft.of("x", 1).cause(-3)));
		assertThrows(IllegalArgumentException.class, () -> l.append(EventDraft.of("x", 1).cause(2)));
		assertThrows(IllegalArgumentException.class, () -> l.append(EventDraft.of("x", 1).cause(99)));
		assertEquals(1, l.size());
	}

	@Test
	void streamCutMidEventKeepsCompleteEvents() throws IOException {
		Ledger original = branching();
		byte[] full = bytes(original);

		// Size of the file holding only the first six events.
		Ledger six = new Ledger();
		for (Event e : original.all().subList(0, 6)) six.restore(e);
		int sixLength = bytes(six).length;
		assertTrue(full.length > sixLength + 4);

		for (int cut : new int[] {sixLength + 1, sixLength + 5, full.length - 1}) {
			byte[] part = new byte[cut];
			System.arraycopy(full, 0, part, 0, cut);
			Ledger back = load(part);
			int expected = cut == full.length - 1 ? 6 : 6;
			assertEquals(expected, back.size(), "cut at " + cut);
			for (int i = 1; i <= expected; i++) assertEquals(original.get(i).orElseThrow(), back.get(i).orElseThrow());
		}

		// Cut exactly between events: all complete events load.
		byte[] exact = new byte[sixLength];
		System.arraycopy(full, 0, exact, 0, sixLength);
		assertEquals(6, load(exact).size());
	}

	@Test
	void emptyAndHeaderOnlyStreams() throws IOException {
		assertEquals(0, load(new byte[0]).size());
		assertEquals(0, load(bytes(new Ledger())).size());
	}

	@Test
	void wrongMagicOrVersionIsRefused() throws IOException {
		byte[] data = bytes(branching());
		byte[] badMagic = data.clone();
		badMagic[0] ^= 0x7F;
		IOException m = assertThrows(IOException.class, () -> load(badMagic));
		assertTrue(m.getMessage().contains("magic"));
		byte[] badVersion = data.clone();
		badVersion[7] = 9;
		IOException v = assertThrows(IOException.class, () -> load(badVersion));
		assertTrue(v.getMessage().contains("version"));
	}

	@Test
	void secondRootIsIndependent() throws IOException {
		Ledger back = load(bytes(branching()));
		assertEquals(List.of(back.get(6).orElseThrow()), back.roots(7));
		assertEquals(List.of(back.get(6).orElseThrow()), back.causeChain(7));
		assertTrue(back.get(6).orElseThrow().isRoot());
	}

	@Test
	void appendingAfterReloadContinuesIds() throws IOException {
		Ledger back = load(bytes(branching()));
		Event next = back.append(EventDraft.of(EventTypes.SLEPT, 20).cause(5));
		assertEquals(8, next.id());
		Event after = back.append(EventDraft.of(EventTypes.SLEPT, 21).cause(8));
		assertEquals(9, after.id());
		assertEquals(Set.of(1L), ids(back.roots(9)));

		// And the extended ledger saves and loads again.
		Ledger again = load(bytes(back));
		assertEquals(9, again.size());
		List<Event> all = new ArrayList<>(back.all());
		assertEquals(all, again.all());
	}
}
