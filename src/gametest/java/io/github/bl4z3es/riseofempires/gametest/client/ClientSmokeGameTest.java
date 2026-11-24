package io.github.bl4z3es.riseofempires.gametest.client;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * The client game-test harness: a real client opens a fresh singleplayer world with the mod loaded,
 * renders it and takes a screenshot. Other client tests (screenshots for review, checks that need a
 * real player's view) build on this. Run with {@code ./gradlew runClientGameTest} (needs a display:
 * {@code xvfb-run -a}).
 */
public final class ClientSmokeGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("roe time");
			context.takeScreenshot("smoke");
		}
	}
}
