package io.github.bl4z3es.riseofempires.mc.block;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

/** The Charter: the item taken from the Hollow Ruin. Set on a lectern, it founds the camp. */
public final class CharterItem {
	private CharterItem() {}

	public static final ResourceKey<Item> KEY =
			ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "charter"));
	public static Item ITEM;

	public static void register() {
		Item.Properties props = new Item.Properties()
				.stacksTo(1)
				.rarity(Rarity.RARE)
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
				.component(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.riseofempires.charter.lore"))));
		ITEM = Registry.register(BuiltInRegistries.ITEM, KEY, new Item(props.setId(KEY)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(ITEM));
	}
}
