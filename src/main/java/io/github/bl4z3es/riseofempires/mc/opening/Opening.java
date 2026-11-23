package io.github.bl4z3es.riseofempires.mc.opening;

import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.block.CharterItem;
import io.github.bl4z3es.riseofempires.mc.block.CharterLectern;

/** The opening of the game: the Hollow Ruin, the Charter inside it, and founding the camp (M1-09, M1-10). */
public final class Opening {
	private Opening() {}

	public static void register() {
		CharterItem.register();
		CharterLectern.register();
		FoundersTent.register();
		HollowRuin.registerGeneration();
		RoeServer.addModule(new HollowRuin());
		RoeServer.addModule(new CharterWatcher());
	}
}
