package io.github.bl4z3es.riseofempires.mc.stockpile;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** Registers the Granary Seal block and item. */
public final class StockpileBlocks {
	private StockpileBlocks() {}

	public static final Identifier GRANARY_SEAL_ID = Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "granary_seal");
	public static Block GRANARY_SEAL;
	public static Item GRANARY_SEAL_ITEM;

	static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, GRANARY_SEAL_ID);
		GRANARY_SEAL = Registry.register(BuiltInRegistries.BLOCK, blockKey, new GranarySeal(
				BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0F).sound(SoundType.WOOD).setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, GRANARY_SEAL_ID);
		GRANARY_SEAL_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(GRANARY_SEAL, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(out -> out.accept(GRANARY_SEAL_ITEM));
	}
}
