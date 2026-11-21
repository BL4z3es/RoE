package io.github.bl4z3es.riseofempires.mc.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.core.model.BlueprintBlock;
import io.github.bl4z3es.riseofempires.core.sim.FoodValues;
import io.github.bl4z3es.riseofempires.core.sim.Survivors;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Content read from data packs (rule E11): blueprints, food values and the survivor pool.
 * Files live under {@code data/riseofempires/} and can be overridden by any data pack.
 */
public final class RoeContent {
	private final Map<String, Blueprint> blueprints;
	private final FoodValues food;
	private final List<Survivors.Template> survivors;

	private RoeContent(Map<String, Blueprint> blueprints, FoodValues food, List<Survivors.Template> survivors) {
		this.blueprints = Map.copyOf(blueprints);
		this.food = food;
		this.survivors = List.copyOf(survivors);
	}

	public Map<String, Blueprint> blueprints() { return blueprints; }
	public FoodValues food() { return food; }
	public List<Survivors.Template> survivors() { return survivors; }

	public static RoeContent load(ResourceManager resources) {
		Map<String, Blueprint> blueprints = new TreeMap<>();
		for (Map.Entry<Identifier, Resource> e : resources.listResources("blueprints", id -> id.getPath().endsWith(".json")).entrySet()) {
			Identifier file = e.getKey();
			String path = file.getPath();
			String id = file.getNamespace() + ":" + path.substring("blueprints/".length(), path.length() - ".json".length());
			try (Reader r = e.getValue().openAsReader()) {
				JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
				List<BlueprintBlock> blocks = new ArrayList<>();
				for (JsonElement b : o.getAsJsonArray("blocks")) {
					JsonArray a = b.getAsJsonArray();
					blocks.add(new BlueprintBlock(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt(),
							a.get(3).getAsString(), a.size() > 4 ? a.get(4).getAsString() : ""));
				}
				blueprints.put(id, new Blueprint(id, o.get("name").getAsString(), blocks));
			} catch (Exception ex) {
				RiseOfEmpires.LOGGER.error("Could not read blueprint {}", file, ex);
			}
		}

		FoodValues food = FoodValues.defaults();
		var foodFile = resources.getResource(Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "food_values.json"));
		if (foodFile.isPresent()) {
			try (Reader r = foodFile.get().openAsReader()) {
				Map<String, Integer> map = new TreeMap<>();
				for (var e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonObject("quarters").entrySet()) {
					map.put(e.getKey(), e.getValue().getAsInt());
				}
				food = new FoodValues(map);
			} catch (Exception ex) {
				RiseOfEmpires.LOGGER.error("Could not read food values", ex);
			}
		}

		List<Survivors.Template> survivors = new ArrayList<>();
		var survivorFile = resources.getResource(Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "survivors/hollowcrown.json"));
		if (survivorFile.isPresent()) {
			try (Reader r = survivorFile.get().openAsReader()) {
				for (JsonElement e : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("survivors")) {
					JsonObject o = e.getAsJsonObject();
					survivors.add(new Survivors.Template(o.get("name").getAsString(), o.get("trait").getAsString(),
							o.get("want").getAsString(), o.get("memory").getAsString()));
				}
			} catch (Exception ex) {
				RiseOfEmpires.LOGGER.error("Could not read survivors", ex);
			}
		}
		RiseOfEmpires.LOGGER.info("Loaded {} blueprints, {} foods, {} survivor templates", blueprints.size(), food.all().size(), survivors.size());
		return new RoeContent(blueprints, food, survivors);
	}
}
