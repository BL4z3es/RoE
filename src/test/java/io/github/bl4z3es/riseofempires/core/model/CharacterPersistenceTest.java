package io.github.bl4z3es.riseofempires.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventDraft;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class CharacterPersistenceTest {
	@Test
	void characterSurvivesSaveAndLoad() throws IOException {
		SimState state = new SimState(42L);
		Event created = state.ledger().append(EventDraft.of(EventTypes.CHARACTER_CREATED, 100)
				.actor(Ref.character(3)));
		Character original = new Character(3L, "Maren Voss", "stubborn", "to see a harvest", Profession.FARMER,
				List.of(new Memory(created.id(), "Saw the old hall burn"), new Memory(0, "Lost a brother é")),
				new Needs(3, 1), Condition.WEAK, 7L);
		state.put(original, created);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StateCodec.write(out, state);
		SimState back = StateCodec.read(new ByteArrayInputStream(out.toByteArray()), state.ledger());

		Character loaded = back.character(3L).orElseThrow();
		assertEquals(original, loaded);
		assertEquals("Maren Voss", loaded.name());
		assertEquals("stubborn", loaded.trait());
		assertEquals("to see a harvest", loaded.want());
		assertEquals(Profession.FARMER, loaded.profession());
		assertEquals(2, loaded.memories().size());
		assertEquals(new Memory(created.id(), "Saw the old hall burn"), loaded.memories().get(0));
		assertEquals(new Needs(3, 1), loaded.needs());
		assertEquals(Condition.WEAK, loaded.condition());
		assertEquals(7L, loaded.settlementId());
		assertEquals(42L, back.seed());
	}
}
