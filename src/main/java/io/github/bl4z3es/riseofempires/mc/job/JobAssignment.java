package io.github.bl4z3es.riseofempires.mc.job;

import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Profession;
import io.github.bl4z3es.riseofempires.core.sim.Jobs;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.entity.CharacterEntity;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Task M1-14: an empty hand on a survivor cycles their profession (none, farmer, builder, gatherer),
 * and they answer in their own words. Any player may do it (law L4).
 */
public final class JobAssignment {
	public static final String KEY_PREFIX = "message.riseofempires.job.";

	private JobAssignment() {}

	/** Called from the body's interaction. */
	public static InteractionResult onInteract(CharacterEntity body, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
		if (player.level().isClientSide()) return InteractionResult.SUCCESS;
		if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		Optional<RoeServer> roe = RoeServer.get();
		if (roe.isEmpty()) return InteractionResult.PASS;
		Optional<Character> rec = roe.get().state().character(body.characterId());
		if (rec.isEmpty() || rec.get().condition().isGone() || rec.get().settlementId() == 0) return InteractionResult.PASS;
		Profession next = next(rec.get().profession());
		Jobs.assign(roe.get().state(), rec.get().id(), next, sp.getStringUUID(), roe.get().now());
		JobModule.sync(roe.get(), body);
		sp.sendSystemMessage(Component.translatable(KEY_PREFIX + next.name().toLowerCase(java.util.Locale.ROOT), rec.get().name()));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** NONE, FARMER, BUILDER, GATHERER, then round to NONE. */
	public static Profession next(Profession p) {
		Profession[] all = Profession.values();
		return all[(p.ordinal() + 1) % all.length];
	}
}
