package io.github.bl4z3es.riseofempires.client;

import io.github.bl4z3es.riseofempires.mc.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.resources.Identifier;

/** Client entry point. Rendering and input only; no simulation (rule E10). */
public final class RiseOfEmpiresClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.PERSON, ctx ->
                new PersonRenderer<>(ctx, Identifier.fromNamespaceAndPath("roe", "textures/entity/person.png")));
        EntityRendererRegistry.register(ModEntities.BARBARIAN, ctx ->
                new PersonRenderer<>(ctx, Identifier.fromNamespaceAndPath("roe", "textures/entity/barbarian.png")));
    }
}
