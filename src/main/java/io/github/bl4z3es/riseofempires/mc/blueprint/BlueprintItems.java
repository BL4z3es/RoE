package io.github.bl4z3es.riseofempires.mc.blueprint;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import io.github.bl4z3es.riseofempires.core.model.Blueprint;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/** The three starter blueprint items and their ghost outline. */
public final class BlueprintItems {
	private BlueprintItems() {}

	public static final Item FARM = create("farm_blueprint", Blueprint.FARM, "Farm");
	public static final Item STABLE = create("stable_blueprint", Blueprint.STABLE, "Stable");
	public static final Item BUNKHOUSE = create("bunkhouse_blueprint", Blueprint.BUNKHOUSE, "Bunkhouse");

	private static Item create(String path, String blueprintId, String displayName) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, path));
		return Registry.register(BuiltInRegistries.ITEM, key,
				new BlueprintItem(new Item.Properties().setId(key).stacksTo(16), blueprintId, displayName));
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(FARM);
			output.accept(STABLE);
			output.accept(BUNKHOUSE);
		});
		RoeServer.addModule(new BlueprintOutline());
	}
}
