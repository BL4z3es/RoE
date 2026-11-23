package io.github.bl4z3es.riseofempires.mc.entity;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registers the mod's entity types. */
public final class RoeEntities {
	private RoeEntities() {}

	public static final ResourceKey<EntityType<?>> CHARACTER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "character"));

	/** The body of a person. Player-sized, tracked from far away so settlements read as populated. */
	public static final EntityType<CharacterEntity> CHARACTER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CHARACTER_KEY,
			EntityType.Builder.of(CharacterEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.eyeHeight(1.62F)
					.clientTrackingRange(10)
					.build(CHARACTER_KEY));

	public static void register() {
		FabricDefaultAttributeRegistry.register(CHARACTER, CharacterEntity.createAttributes());
	}
}
