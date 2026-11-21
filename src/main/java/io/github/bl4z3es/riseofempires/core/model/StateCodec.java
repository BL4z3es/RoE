package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Binary save and load for {@link SimState}. The Ledger is saved separately by
 * {@code LedgerCodec}; this file holds everything else.
 */
public final class StateCodec {
	private StateCodec() {}

	public static final int MAGIC = 0x524F4553; // "ROES"
	public static final int VERSION = 1;

	public static void write(OutputStream stream, SimState s) throws IOException {
		DataOutputStream out = new DataOutputStream(stream);
		out.writeInt(MAGIC);
		out.writeInt(VERSION);
		out.writeLong(s.seed());

		out.writeInt(s.characters().size());
		for (Character c : s.characters()) {
			out.writeLong(c.id());
			Bin.string(out, c.name());
			Bin.string(out, c.trait());
			Bin.string(out, c.want());
			Bin.string(out, c.profession().name());
			out.writeInt(c.memories().size());
			for (Memory m : c.memories()) {
				out.writeLong(m.eventId());
				Bin.string(out, m.text());
			}
			out.writeInt(c.needs().missedMeals());
			out.writeInt(c.needs().nightsRough());
			Bin.string(out, c.condition().name());
			out.writeLong(c.settlementId());
		}

		out.writeInt(s.settlements().size());
		for (Settlement t : s.settlements()) {
			out.writeLong(t.id());
			Bin.string(out, t.name());
			Bin.string(out, t.founder());
			Bin.place(out, t.centre());
			out.writeInt(t.claimRadius());
			out.writeInt(t.population().size());
			for (long id : t.population()) out.writeLong(id);
			Bin.place(out, t.granary());
			out.writeLong(t.foundedEventId());
		}

		out.writeInt(s.stockpiles().size());
		for (Stockpile p : s.stockpiles()) {
			out.writeLong(p.settlementId());
			Bin.intMap(out, p.counted());
			Bin.intMap(out, p.pending());
		}

		out.writeInt(s.raids().size());
		for (Raid r : s.raids()) {
			out.writeLong(r.id());
			out.writeLong(r.settlementId());
			out.writeInt(r.size());
			out.writeLong(r.plannedEventId());
			Bin.place(out, r.origin());
			out.writeLong(r.sightTime());
			out.writeLong(r.arriveTime());
			Bin.string(out, r.stage().name());
			out.writeInt(r.raidersLeft());
		}

		out.writeInt(s.buildSites().size());
		for (BuildSite b : s.buildSites()) {
			out.writeLong(b.id());
			out.writeLong(b.settlementId());
			Bin.string(out, b.blueprintId());
			Bin.place(out, b.origin());
			out.writeInt(b.rotation());
			out.writeInt(b.placed());
			out.writeInt(b.total());
			out.writeLong(b.placedEventId());
		}

		out.writeInt(s.farms().size());
		for (Map.Entry<Long, List<Plot>> e : s.farms().entrySet()) {
			out.writeLong(e.getKey());
			out.writeInt(e.getValue().size());
			for (Plot p : e.getValue()) {
				Bin.place(out, p.pos());
				Bin.string(out, p.crop());
				out.writeInt(p.age());
			}
		}

		out.writeInt(s.actOneOutcomes().size());
		for (Map.Entry<Long, ActOutcome> e : s.actOneOutcomes().entrySet()) {
			out.writeLong(e.getKey());
			Bin.string(out, e.getValue().name());
		}

		out.writeInt(s.foundedBy().size());
		for (Map.Entry<String, Long> e : s.foundedBy().entrySet()) {
			Bin.string(out, e.getKey());
			out.writeLong(e.getValue());
		}

		out.writeInt(s.facts().size());
		for (Map.Entry<String, String> e : s.facts().entrySet()) {
			Bin.string(out, e.getKey());
			Bin.string(out, e.getValue());
		}

		out.writeInt(s.counters().size());
		for (Map.Entry<String, Long> e : s.counters().entrySet()) {
			Bin.string(out, e.getKey());
			out.writeLong(e.getValue());
		}

		out.writeInt(s.cursors().size());
		for (Map.Entry<String, Long> e : s.cursors().entrySet()) {
			Bin.string(out, e.getKey());
			out.writeLong(e.getValue());
		}
		out.flush();
	}

	public static SimState read(InputStream stream, Ledger ledger) throws IOException {
		DataInputStream in = new DataInputStream(stream);
		if (in.readInt() != MAGIC) throw new IOException("Not a Rise of Empires state file (bad magic number)");
		int version = in.readInt();
		if (version != VERSION) {
			throw new IOException("Unsupported state version " + version + " (this game reads version " + VERSION + ")");
		}
		SimState s = new SimState(in.readLong(), ledger);

		int characters = Bin.count(in);
		for (int i = 0; i < characters; i++) {
			long id = in.readLong();
			String name = Bin.string(in);
			String trait = Bin.string(in);
			String want = Bin.string(in);
			Profession profession = Profession.valueOf(Bin.string(in));
			int memoryCount = Bin.count(in);
			List<Memory> memories = new ArrayList<>();
			for (int m = 0; m < memoryCount; m++) {
				long eventId = in.readLong();
				memories.add(new Memory(eventId, Bin.string(in)));
			}
			Needs needs = new Needs(in.readInt(), in.readInt());
			Condition condition = Condition.valueOf(Bin.string(in));
			long settlementId = in.readLong();
			s.restore(new Character(id, name, trait, want, profession, memories, needs, condition, settlementId));
		}

		int settlements = Bin.count(in);
		for (int i = 0; i < settlements; i++) {
			long id = in.readLong();
			String name = Bin.string(in);
			String founder = Bin.string(in);
			var centre = Bin.place(in);
			int radius = in.readInt();
			int populationCount = Bin.count(in);
			List<Long> population = new ArrayList<>();
			for (int p = 0; p < populationCount; p++) population.add(in.readLong());
			var granary = Bin.place(in);
			long foundedEventId = in.readLong();
			s.restore(new Settlement(id, name, founder, centre, radius, population, granary, foundedEventId));
		}

		int stockpiles = Bin.count(in);
		for (int i = 0; i < stockpiles; i++) {
			long id = in.readLong();
			var counted = Bin.intMap(in);
			var pending = Bin.intMap(in);
			s.restore(new Stockpile(id, counted, pending));
		}

		int raids = Bin.count(in);
		for (int i = 0; i < raids; i++) {
			long id = in.readLong();
			long settlementId = in.readLong();
			int size = in.readInt();
			long plannedEventId = in.readLong();
			var origin = Bin.place(in);
			long sightTime = in.readLong();
			long arriveTime = in.readLong();
			RaidStage stage = RaidStage.valueOf(Bin.string(in));
			int left = in.readInt();
			s.restore(new Raid(id, settlementId, size, plannedEventId, origin, sightTime, arriveTime, stage, left));
		}

		int sites = Bin.count(in);
		for (int i = 0; i < sites; i++) {
			long id = in.readLong();
			long settlementId = in.readLong();
			String blueprintId = Bin.string(in);
			var origin = Bin.place(in);
			int rotation = in.readInt();
			int placed = in.readInt();
			int total = in.readInt();
			long placedEventId = in.readLong();
			s.restore(new BuildSite(id, settlementId, blueprintId, origin, rotation, placed, total, placedEventId));
		}

		int farms = Bin.count(in);
		for (int i = 0; i < farms; i++) {
			long settlementId = in.readLong();
			int plotCount = Bin.count(in);
			List<Plot> plots = new ArrayList<>();
			for (int p = 0; p < plotCount; p++) {
				var pos = Bin.place(in);
				String crop = Bin.string(in);
				plots.add(new Plot(pos, crop, in.readInt()));
			}
			s.restoreFarm(settlementId, plots);
		}

		int outcomes = Bin.count(in);
		for (int i = 0; i < outcomes; i++) {
			long settlementId = in.readLong();
			s.restoreActOne(settlementId, ActOutcome.valueOf(Bin.string(in)));
		}

		int founders = Bin.count(in);
		for (int i = 0; i < founders; i++) {
			String player = Bin.string(in);
			s.restoreFounder(player, in.readLong());
		}

		int facts = Bin.count(in);
		for (int i = 0; i < facts; i++) {
			String key = Bin.string(in);
			s.restoreFact(key, Bin.string(in));
		}

		int counters = Bin.count(in);
		for (int i = 0; i < counters; i++) {
			String key = Bin.string(in);
			s.restoreCounter(key, in.readLong());
		}

		int cursors = Bin.count(in);
		for (int i = 0; i < cursors; i++) {
			String key = Bin.string(in);
			s.setCursor(key, in.readLong());
		}
		return s;
	}
}
