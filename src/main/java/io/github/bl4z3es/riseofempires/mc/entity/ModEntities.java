package io.github.bl4z3es.riseofempires.mc.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * {@code roe:person} and {@code roe:barbarian}. Neither is saved with the chunk: a body is only
 * a view of a record in the simulation, and {@link Bodies} puts it back when the camp is watched.
 */
public final class ModEntities {
    private ModEntities() {}

    public static final EntityType<PersonEntity> PERSON = register("person",
            EntityType.Builder.of(PersonEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10).noSave());

    public static final EntityType<BarbarianEntity> BARBARIAN = register("barbarian",
            EntityType.Builder.of(BarbarianEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).eyeHeight(1.62F)
                    .clientTrackingRange(10).noSave());

    private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String path, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("roe", path));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(PERSON, PersonEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(BARBARIAN, BarbarianEntity.createAttributes());
    }
}
