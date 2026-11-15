package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.Ledger;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The append-only event log, one {@link Event#encode()} line per event, at
 * {@code <world>/data/roe/ledger.log}. Lines are queued on the server thread and written by a
 * background thread so no file call happens during a tick (rule E7).
 */
public final class LedgerLog {
    private final Path file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "roe-ledger-writer");
        t.setDaemon(true);
        return t;
    });
    private final List<String> pending = new ArrayList<>();

    public LedgerLog(Path file) {
        this.file = file;
    }

    public Path file() { return file; }

    /**
     * Reads the first {@code keep} events into the ledger and cuts the file to them. Events
     * after the last world save are dropped: the world state they changed was never saved.
     * Runs once at server start, before the first tick.
     */
    public void restoreInto(Ledger ledger, long keep) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            Files.createFile(file);
            return;
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        int n = 0;
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) continue;
            if (n >= keep) break;
            ledger.restore(Event.decode(line));
            kept.add(line);
            n++;
        }
        if (kept.size() != lines.stream().filter(l -> !l.isEmpty()).count()) {
            RiseOfEmpires.LOG.warn("[riseofempires] Dropped {} events written after the last save",
                    lines.size() - kept.size());
            Files.write(file, kept, StandardCharsets.UTF_8);
        }
    }

    /** Queues a line. Called from the Ledger's append listener on the server thread. */
    public synchronized void queue(Event event) {
        pending.add(event.encode());
        if (pending.size() >= io.github.bl4z3es.riseofempires.mc.McTuning.LEDGER_FLUSH_LINES) flush();
    }

    /** Hands queued lines to the writer thread. */
    public synchronized void flush() {
        if (pending.isEmpty()) return;
        List<String> batch = new ArrayList<>(pending);
        pending.clear();
        writer.execute(() -> {
            try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (String line : batch) { w.write(line); w.newLine(); }
            } catch (IOException e) {
                RiseOfEmpires.LOG.error("[riseofempires] Could not write the event log", e);
            }
        });
    }

    /** Flushes and waits for the writer. Called when the world is saved or the server stops. */
    public void flushAndWait() {
        flush();
        try {
            writer.submit(() -> { }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            RiseOfEmpires.LOG.error("[riseofempires] Event log writer did not finish", e);
        }
    }

    public void close() {
        flushAndWait();
        writer.shutdown();
    }
}
