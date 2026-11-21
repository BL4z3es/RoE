package io.github.bl4z3es.riseofempires;

import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.command.RoeCommands;
import io.github.bl4z3es.riseofempires.mc.story.Storyteller;
import io.github.bl4z3es.riseofempires.mc.farm.FarmSync;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterBodies;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entry point. Runs on both the dedicated server and the client's integrated server. */
public final class RiseOfEmpires implements ModInitializer {
	public static final String MOD_ID = "riseofempires";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RoeServer.register();
		RoeCommands.register();
		Storyteller.register();
		io.github.bl4z3es.riseofempires.mc.stockpile.StockpileSync.register();
		FarmSync.register();
		io.github.bl4z3es.riseofempires.mc.blueprint.BlueprintItems.register();
		io.github.bl4z3es.riseofempires.mc.opening.Opening.register();
		CharacterBodies.register();
		io.github.bl4z3es.riseofempires.mc.raid.RaidDirector.register();
		ServerLifecycleEvents.SERVER_STARTED.register(server -> LOGGER.info("initialised"));
	}
}
