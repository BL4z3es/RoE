package io.github.bl4z3es.riseofempires.mc.world;

import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.model.BuildSite;
import io.github.bl4z3es.riseofempires.core.sim.Building;
import io.github.bl4z3es.riseofempires.core.sim.LedgerTier;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

/**
 * World changes made off-camera (rule E6). Each edit waits in a queue for its chunk and is applied,
 * in order, once that chunk is loaded and no player is close enough to see it change. Chunks are
 * never force-loaded for this.
 */
public final class DeferredEdits implements RoeServer.Module {
	/** One block to set. {@code blockState} is already rotated text; {@code turns} is applied on parse. */
	public record Edit(String dimension, int x, int y, int z, String blockState, int turns) {
		long chunkKey() { return ChunkPos.pack(x >> 4, z >> 4); }
	}

	public static final DeferredEdits INSTANCE = new DeferredEdits();
	private static final int FILE_VERSION = 1;

	/** Chunk key to edits, per dimension, in the order they were queued. */
	private final Map<String, Map<Long, List<Edit>>> queue = new LinkedHashMap<>();

	private DeferredEdits() {}

	public void clear() { queue.clear(); }

	public void add(Edit e) {
		queue.computeIfAbsent(e.dimension(), k -> new LinkedHashMap<>()).computeIfAbsent(e.chunkKey(), k -> new ArrayList<>()).add(e);
	}

	public int size() {
		int n = 0;
		for (Map<Long, List<Edit>> m : queue.values()) for (List<Edit> l : m.values()) n += l.size();
		return n;
	}

	/** Queues the blocks the Ledger tier built while the settlement was unloaded. */
	public static void queueBuilt(RoeServer roe, List<LedgerTier.BuiltBlock> built) {
		for (LedgerTier.BuiltBlock b : built) {
			BuildSite site = roe.state().buildSite(b.siteId()).orElse(null);
			if (site == null) continue;
			Blueprint bp = roe.content().blueprints().get(site.blueprintId());
			if (bp == null || b.index() >= bp.blocks().size()) continue;
			BlueprintBlock block = bp.blocks().get(b.index());
			Place p = Building.worldPos(site, block);
			INSTANCE.add(new Edit(p.dimension(), p.x(), p.y(), p.z(), block.blockState(), site.rotation()));
		}
	}

	@Override
	public void tick(RoeServer roe, long now) {
		for (Map.Entry<String, Map<Long, List<Edit>>> dim : queue.entrySet()) {
			ServerLevel level = roe.level(new Place(dim.getKey(), 0, 0, 0));
			Iterator<Map.Entry<Long, List<Edit>>> it = dim.getValue().entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<Long, List<Edit>> chunk = it.next();
				List<Edit> edits = chunk.getValue();
				Edit first = edits.get(0);
				BlockPos probe = new BlockPos(first.x(), first.y(), first.z());
				if (!level.isLoaded(probe) || WorldBlocks.playerNear(level, probe)) continue;
				for (Edit e : edits) {
					level.setBlock(new BlockPos(e.x(), e.y(), e.z()), WorldBlocks.parse(level, e.blockState(), e.turns()), Block.UPDATE_ALL);
				}
				it.remove();
			}
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
			out.writeInt(FILE_VERSION);
			for (Map<Long, List<Edit>> m : queue.values()) {
				for (List<Edit> l : m.values()) {
					for (Edit e : l) {
						out.writeBoolean(true);
						out.writeUTF(e.dimension());
						out.writeInt(e.x());
						out.writeInt(e.y());
						out.writeInt(e.z());
						out.writeUTF(e.blockState());
						out.writeInt(e.turns());
					}
				}
			}
			out.writeBoolean(false);
		}
	}

	public void load(Path file) throws IOException {
		queue.clear();
		if (!Files.exists(file)) return;
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			if (in.readInt() != FILE_VERSION) throw new IOException("Unknown deferred-edit file version in " + file);
			while (in.readBoolean()) {
				add(new Edit(in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readUTF(), in.readInt()));
			}
		} catch (EOFException e) {
			// A save cut short: keep what was read.
		}
	}
}
