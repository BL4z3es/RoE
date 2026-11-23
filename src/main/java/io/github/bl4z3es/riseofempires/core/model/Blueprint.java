package io.github.bl4z3es.riseofempires.core.model;

import java.util.List;

/** A building that builders construct block by block, in list order (bottom up). Loaded from data packs. */
public record Blueprint(String id, String name, List<BlueprintBlock> blocks) {
	public static final String FARM = "riseofempires:farm";
	public static final String STABLE = "riseofempires:stable";
	public static final String BUNKHOUSE = "riseofempires:bunkhouse";

	public Blueprint {
		blocks = List.copyOf(blocks);
	}
}
