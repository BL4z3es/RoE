package io.github.bl4z3es.riseofempires.mc.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/** The mod's items and blocks: {@code roe:charter} and {@code roe:granary_seal}. */
public final class ModItems {
    private ModItems() {}

    public static final Block GRANARY_SEAL = Registry.register(BuiltInRegistries.BLOCK, blockKey("granary_seal"),
            new GranarySealBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F)
                    .sound(SoundType.WOOD).ignitedByLava().setId(blockKey("granary_seal"))));

    public static final Item GRANARY_SEAL_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey("granary_seal"),
            new BlockItem(GRANARY_SEAL, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey("granary_seal"))));

    public static final Item CHARTER = Registry.register(BuiltInRegistries.ITEM, itemKey("charter"),
            new CharterItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).setId(itemKey("charter"))));

    public static final Item PLAN = Registry.register(BuiltInRegistries.ITEM, itemKey("plan"),
            new PlanItem(new Item.Properties().stacksTo(1).setId(itemKey("plan"))));

    /** Forces the fields above to load during mod initialisation. */
    public static void register() {}

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("roe", path));
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("roe", path));
    }
}
