package io.github.bl4z3es.riseofempires.mc.opening;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads {@code data/riseofempires/structures/<name>.json} (rule E11: content is data). */
public final class StructureFiles {
	private StructureFiles() {}

	public static Optional<Structure> load(ResourceManager resources, String name) {
		var file = resources.getResource(Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "structures/" + name + ".json"));
		if (file.isEmpty()) {
			RiseOfEmpires.LOGGER.error("Structure {} is missing", name);
			return Optional.empty();
		}
		try (Reader r = file.get().openAsReader()) {
			JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
			List<Structure.Block> blocks = new ArrayList<>();
			for (JsonElement b : o.getAsJsonArray("blocks")) {
				JsonArray a = b.getAsJsonArray();
				blocks.add(new Structure.Block(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(), a.get(3).getAsString()));
			}
			return Optional.of(new Structure(o.get("name").getAsString(), blocks));
		} catch (Exception ex) {
			RiseOfEmpires.LOGGER.error("Could not read structure {}", name, ex);
			return Optional.empty();
		}
	}
}
