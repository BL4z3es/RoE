package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import io.github.bl4z3es.riseofempires.core.ledger.LedgerCodec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The world's event log: one append-only file, {@code <world>/data/riseofempires/ledger.bin}.
 * Saving appends only the events written since the last save; nothing already on disk is rewritten.
 */
public final class LedgerFile {
	private final Path file;
	private long persistedUpTo;

	public LedgerFile(Path worldDataDir) {
		this.file = worldDataDir.resolve("riseofempires").resolve("ledger.bin");
	}

	public Path path() { return file; }

	public Ledger load() throws IOException {
		if (!Files.exists(file)) {
			persistedUpTo = 0;
			return new Ledger();
		}
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			Ledger ledger = LedgerCodec.readAll(in);
			persistedUpTo = ledger.lastId();
			return ledger;
		}
	}

	/** Appends every event after the last one saved. Writes the header if the file is new. */
	public void append(Ledger ledger) throws IOException {
		if (ledger.lastId() <= persistedUpTo) return;
		Files.createDirectories(file.getParent());
		boolean fresh = !Files.exists(file) || Files.size(file) == 0;
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND)))) {
			if (fresh) LedgerCodec.writeHeader(out);
			for (Event e : ledger.after(persistedUpTo)) LedgerCodec.writeEvent(out, e);
		}
		persistedUpTo = ledger.lastId();
	}
}
