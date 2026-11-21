package io.github.bl4z3es.riseofempires.mc.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The simulation's records, kept in Minecraft's saved-data system (task M1-02). The bytes are written
 * by {@code core.model.StateCodec}; this class only carries them. The Ledger itself lives in its own
 * append-only file, see {@link LedgerFile}.
 */
public final class RoeSavedData extends SavedData {
	private static final Codec<RoeSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BYTE_BUFFER.fieldOf("state").forGetter(d -> ByteBuffer.wrap(d.bytes))
	).apply(i, RoeSavedData::new));

	// Our data has no Minecraft structure to upgrade. RANDOM_SEQUENCES is a pass-through fixer.
	public static final SavedDataType<RoeSavedData> TYPE = new SavedDataType<>(
			Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "state"), RoeSavedData::new, CODEC,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private byte[] bytes;

	public RoeSavedData() { this.bytes = new byte[0]; }

	private RoeSavedData(ByteBuffer buffer) {
		ByteBuffer copy = buffer.duplicate();
		this.bytes = new byte[copy.remaining()];
		copy.get(this.bytes);
	}

	public byte[] bytes() { return bytes; }

	public void setBytes(byte[] next) {
		this.bytes = next;
		setDirty();
	}
}
