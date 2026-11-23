package io.github.bl4z3es.riseofempires.mc.blueprint;

import io.github.bl4z3es.riseofempires.mc.RoeServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** A rolled-up plan. Use it on the ground inside your camp to lay out the building's outline. */
public final class BlueprintItem extends Item {
	private final String blueprintId;
	private final String displayName;

	public BlueprintItem(Properties properties, String blueprintId, String displayName) {
		super(properties);
		this.blueprintId = blueprintId;
		this.displayName = displayName;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		RoeServer roe = RoeServer.get().orElse(null);
		if (roe == null) {
			return InteractionResult.PASS;
		}
		// The front of the building faces the player, so the plan opens toward the person who laid it out.
		int rotation = context.getHorizontalDirection().getOpposite().get2DDataValue();
		BlueprintPlacement.Result result = BlueprintPlacement.place(roe, level, context.getClickedPos(), player.getUUID().toString(), blueprintId, rotation);
		switch (result.outcome()) {
			case PLACED -> {
				player.sendSystemMessage(Component.translatable("message.riseofempires.blueprint_placed", displayName));
				if (!player.hasInfiniteMaterials()) {
					context.getItemInHand().shrink(1);
				}
			}
			case OUTSIDE_CAMP -> player.sendSystemMessage(Component.translatable("message.riseofempires.blueprint_outside_camp"));
			case UNKNOWN_BLUEPRINT -> player.sendSystemMessage(Component.translatable("message.riseofempires.blueprint_unknown"));
		}
		return InteractionResult.SUCCESS_SERVER;
	}
}
