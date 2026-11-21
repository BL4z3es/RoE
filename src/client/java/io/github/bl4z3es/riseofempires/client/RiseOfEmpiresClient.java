package io.github.bl4z3es.riseofempires.client;

import io.github.bl4z3es.riseofempires.client.entity.CharacterRenderer;
import net.fabricmc.api.ClientModInitializer;

/** Client entry point. Rendering and input only; no simulation runs here (rule E10). */
public final class RiseOfEmpiresClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CharacterRenderer.register();
		io.github.bl4z3es.riseofempires.client.raid.RaiderRenderer.register();
	}
}
