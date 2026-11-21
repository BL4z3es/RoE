package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Small read and write helpers shared by {@link StateCodec}. Strings are an int length then UTF-8 bytes. */
final class Bin {
	private Bin() {}

	private static final int MAX_LENGTH = 1 << 24; // PROPOSED: not in Balance yet

	static void string(DataOutputStream out, String s) throws IOException {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		out.writeInt(bytes.length);
		out.write(bytes);
	}

	static String string(DataInputStream in) throws IOException {
		byte[] bytes = new byte[count(in)];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	static int count(DataInputStream in) throws IOException {
		int n = in.readInt();
		if (n < 0 || n > MAX_LENGTH) throw new IOException("State file is damaged: bad length " + n);
		return n;
	}

	static void place(DataOutputStream out, Place p) throws IOException {
		out.writeBoolean(p != null);
		if (p == null) return;
		string(out, p.dimension());
		out.writeInt(p.x());
		out.writeInt(p.y());
		out.writeInt(p.z());
	}

	static Place place(DataInputStream in) throws IOException {
		if (!in.readBoolean()) return null;
		String dimension = string(in);
		return new Place(dimension, in.readInt(), in.readInt(), in.readInt());
	}

	static void intMap(DataOutputStream out, Map<String, Integer> map) throws IOException {
		out.writeInt(map.size());
		for (Map.Entry<String, Integer> e : new TreeMap<>(map).entrySet()) {
			string(out, e.getKey());
			out.writeInt(e.getValue());
		}
	}

	static Map<String, Integer> intMap(DataInputStream in) throws IOException {
		int n = count(in);
		Map<String, Integer> map = new TreeMap<>();
		for (int i = 0; i < n; i++) {
			String key = string(in);
			map.put(key, in.readInt());
		}
		return map;
	}
}
