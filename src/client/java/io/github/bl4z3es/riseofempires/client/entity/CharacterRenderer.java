package io.github.bl4z3es.riseofempires.client.entity;

import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import io.github.bl4z3es.riseofempires.mc.entity.RoeEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** Draws a character's body as a person, using the vanilla player model and one of the vanilla skins. */
public final class CharacterRenderer extends HumanoidMobRenderer<CharacterEntity, CharacterRenderer.State, HumanoidModel<CharacterRenderer.State>> {
	private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};

	/** Render state that also carries which skin to draw. */
	public static final class State extends HumanoidRenderState {
		Identifier texture = skin(0);
	}

	public CharacterRenderer(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
	}

	public static void register() {
		EntityRendererRegistry.register(RoeEntities.CHARACTER, CharacterRenderer::new);
	}

	private static Identifier skin(int index) {
		return Identifier.withDefaultNamespace("textures/entity/player/wide/" + SKINS[Math.floorMod(index, SKINS.length)] + ".png");
	}

	@Override
	public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(CharacterEntity entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.texture = skin(entity.getUUID().hashCode());
	}

	@Override
	public Identifier getTextureLocation(State state) { return state.texture; }
}
