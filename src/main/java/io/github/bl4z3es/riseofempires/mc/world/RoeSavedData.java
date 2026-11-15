package io.github.bl4z3es.riseofempires.mc.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The simulation's world state and the deferred block edits, kept in Minecraft's saved-data
 * system ({@code data/roe/world.dat}). Text is stored in pieces because one NBT string holds at
 * most 65 535 bytes.
 */
public final class RoeSavedData extends SavedData {
    private static final int PIECE = 16_000;

    public static final Codec<RoeSavedData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("state", List.of()).forGetter(d -> split(d.state)),
            Codec.LONG.optionalFieldOf("ledger_size", 0L).forGetter(d -> d.ledgerSize),
            Codec.STRING.listOf().optionalFieldOf("deferred", List.of()).forGetter(d -> split(d.deferred))
    ).apply(i, (state, size, deferred) -> new RoeSavedData(String.join("", state), size, String.join("", deferred))));

    public static final SavedDataType<RoeSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("roe", "world"), RoeSavedData::new, CODEC, null);

    private String state;
    private long ledgerSize;
    private String deferred;

    public RoeSavedData() {
        this("", 0, "");
    }

    private RoeSavedData(String state, long ledgerSize, String deferred) {
        this.state = state;
        this.ledgerSize = ledgerSize;
        this.deferred = deferred;
    }

    public String state() { return state; }
    public long ledgerSize() { return ledgerSize; }
    public String deferred() { return deferred; }

    public void store(String state, long ledgerSize, String deferred) {
        this.state = state;
        this.ledgerSize = ledgerSize;
        this.deferred = deferred;
        setDirty();
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < s.length(); i += PIECE) out.add(s.substring(i, Math.min(s.length(), i + PIECE)));
        return out;
    }
}
