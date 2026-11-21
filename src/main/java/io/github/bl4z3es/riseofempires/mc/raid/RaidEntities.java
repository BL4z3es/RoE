package io.github.bl4z3es.riseofempires.mc.raid;

import io.github.bl4z3es.riseofempires.RiseOfEmpires;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registers the raider entity type. */
public final class RaidEntities {
	private RaidEntities() {}

	public static final ResourceKey<EntityType<?>> RAIDER_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(RiseOfEmpires.MOD_ID, "raider"));

	/** A barbarian: a person-sized fighter in leather with a diamond axe. */
	public static final EntityType<RaiderEntity> RAIDER = Registry.register(BuiltInRegistries.ENTITY_TYPE, RAIDER_KEY,
			EntityType.Builder.of(RaiderEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.74F)
					.clientTrackingRange(10)
					.build(RAIDER_KEY));

	static void register() {
		FabricDefaultAttributeRegistry.register(RAIDER, RaiderEntity.createAttributes());
	}
}
