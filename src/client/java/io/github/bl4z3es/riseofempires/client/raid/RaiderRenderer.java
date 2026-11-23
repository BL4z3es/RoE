package io.github.bl4z3es.riseofempires.client.raid;

import io.github.bl4z3es.riseofempires.mc.raid.RaidEntities;
import io.github.bl4z3es.riseofempires.mc.raid.RaiderEntity;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Draws a raider as a person in a vanilla skin, with its leather armour and diamond axe showing. */
public final class RaiderRenderer extends HumanoidMobRenderer<RaiderEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/wide/kai.png");

	public RaiderRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
		ArmorModelSet<HumanoidModel<HumanoidRenderState>> armor =
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new);
		addLayer(new HumanoidArmorLayer<>(this, armor, armor, context.getEquipmentRenderer()));
	}

	public static void register() {
		EntityRendererRegistry.register(RaidEntities.RAIDER, RaiderRenderer::new);
	}

	@Override
	public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) { return TEXTURE; }
}
