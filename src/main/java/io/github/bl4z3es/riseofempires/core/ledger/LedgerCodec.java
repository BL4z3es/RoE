package io.github.bl4z3es.riseofempires.core.ledger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary format for the Ledger: a header, then events one after another with no footer, so a new
 * event can be appended to the file at any time. If the game crashes in the middle of an append,
 * {@link #readAll} drops the partial last event and keeps everything before it.
 */
public final class LedgerCodec {
	private LedgerCodec() {}

	public static final int MAGIC = 0x524F454C; // "ROEL"
	public static final int VERSION = 1;

	/** Largest text or list we accept when reading, so a damaged file cannot ask for gigabytes. */
	private static final int MAX_LENGTH = 1 << 24; // PROPOSED: not in Balance yet

	public static void writeHeader(DataOutputStream out) throws IOException {
		out.writeInt(MAGIC);
		out.writeInt(VERSION);
	}

	public static void writeEvent(DataOutputStream out, Event e) throws IOException {
		out.writeLong(e.id());
		out.writeLong(e.time());
		out.writeBoolean(e.place() != null);
		if (e.place() != null) {
			writeString(out, e.place().dimension());
			out.writeInt(e.place().x());
			out.writeInt(e.place().y());
			out.writeInt(e.place().z());
		}
		writeString(out, e.type());
		writeRefs(out, e.actors());
		out.writeInt(e.causes().size());
		for (long c : e.causes()) out.writeLong(c);
		out.writeInt(e.effects().size());
		for (Effect f : e.effects()) {
			writeRef(out, f.target());
			writeString(out, f.what());
			writeString(out, f.detail());
		}
		writeRefs(out, e.witnesses());
	}

	public static Event readEvent(DataInputStream in) throws IOException {
		long id = in.readLong();
		long time = in.readLong();
		Place place = null;
		if (in.readBoolean()) {
			String dimension = readString(in);
			place = new Place(dimension, in.readInt(), in.readInt(), in.readInt());
		}
		String type = readString(in);
		List<Ref> actors = readRefs(in);
		int causeCount = readCount(in);
		List<Long> causes = new ArrayList<>();
		for (int i = 0; i < causeCount; i++) causes.add(in.readLong());
		int effectCount = readCount(in);
		List<Effect> effects = new ArrayList<>();
		for (int i = 0; i < effectCount; i++) {
			Ref target = readRef(in);
			String what = readString(in);
			String detail = readString(in);
			effects.add(new Effect(target, what, detail));
		}
		List<Ref> witnesses = readRefs(in);
		return new Event(id, time, place, type, actors, causes, effects, witnesses);
	}

	public static void writeAll(OutputStream out, Ledger ledger) throws IOException {
		DataOutputStream data = new DataOutputStream(out);
		writeHeader(data);
		for (Event e : ledger.all()) writeEvent(data, e);
		data.flush();
	}

	public static Ledger readAll(InputStream in) throws IOException {
		Ledger ledger = new Ledger();
		PushbackInputStream peek = new PushbackInputStream(in, 1);
		int first = peek.read();
		if (first < 0) return ledger; // empty file: nothing was ever written
		peek.unread(first);
		DataInputStream data = new DataInputStream(peek);
		try {
			int magic = data.readInt();
			if (magic != MAGIC) throw new IOException("Not a Rise of Empires ledger file (bad magic number)");
			int version = data.readInt();
			if (version != VERSION) {
				throw new IOException("Unsupported ledger version " + version + " (this game reads version " + VERSION + ")");
			}
		} catch (EOFException e) {
			throw new IOException("Ledger file ends inside its header", e);
		}
		while (true) {
			int next = peek.read();
			if (next < 0) break; // clean end between events
			peek.unread(next);
			Event event;
			try {
				event = readEvent(data);
			} catch (EOFException cut) {
				break; // crash during an append: drop the partial event
			}
			try {
				ledger.restore(event);
			} catch (IllegalArgumentException bad) {
				throw new IOException("Ledger file is damaged: " + bad.getMessage(), bad);
			}
		}
		return ledger;
	}

	// Helpers

	private static void writeString(DataOutputStream out, String s) throws IOException {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	private static String readString(DataInputStream in) throws IOException {
		int length = readCount(in);
		byte[] bytes = new byte[length];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static int readCount(DataInputStream in) throws IOException {
		int n = in.readInt();
		if (n < 0 || n > MAX_LENGTH) throw new IOException("Ledger file is damaged: bad length " + n);
		return n;
	}

	private static void writeRef(DataOutputStream out, Ref r) throws IOException {
		writeString(out, r.kind());
		writeString(out, r.id());
	}

	private static Ref readRef(DataInputStream in) throws IOException {
		String kind = readString(in);
		return new Ref(kind, readString(in));
	}

	private static void writeRefs(DataOutputStream out, List<Ref> refs) throws IOException {
		out.writeInt(refs.size());
		for (Ref r : refs) writeRef(out, r);
	}

	private static List<Ref> readRefs(DataInputStream in) throws IOException {
		int n = readCount(in);
		List<Ref> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(readRef(in));
		return out;
	}
}
