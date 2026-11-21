package io.github.bl4z3es.riseofempires.mc.story;

import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Event;
import io.github.bl4z3es.riseofempires.core.ledger.EventTypes;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Raid;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import io.github.bl4z3es.riseofempires.core.model.SimState;
import io.github.bl4z3es.riseofempires.core.sim.ActOne;
import io.github.bl4z3es.riseofempires.core.sim.Advice;
import io.github.bl4z3es.riseofempires.core.sim.Calendar;
import io.github.bl4z3es.riseofempires.core.sim.GameTime;
import io.github.bl4z3es.riseofempires.core.Balance;
import io.github.bl4z3es.riseofempires.mc.RoeServer;
import io.github.bl4z3es.riseofempires.mc.world.DeferredEdits;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Tells the founder what is happening, in the survivors' voices (task M1-22, and the warnings of
 * M1-20 and M1-21). Everything said comes from Ledger events or from {@link Advice}, so every line has
 * a cause the player can look up with {@code /roe why}. No pop-ups: chat lines from people in the camp.
 */
public final class Storyteller implements RoeServer.Module {
	/** Ticks between two pieces of advice when nothing has changed. */
	private static final long ADVICE_INTERVAL_TICKS = 2400; // PROPOSED: not in Balance yet

	private final Map<Long, String> lastAdvice = new HashMap<>();
	private final Map<Long, Long> lastAdviceTime = new HashMap<>();
	private long lastEventSeen = -1;

	public static void register() {
		RoeServer.addModule(new Storyteller());
	}

	@Override
	public void tick(RoeServer roe, long now) {
		SimState s = roe.state();
		if (lastEventSeen < 0) lastEventSeen = s.ledger().lastId(); // do not replay old news after a restart
		for (Event e : s.ledger().after(lastEventSeen)) announce(roe, e);
		lastEventSeen = s.ledger().lastId();
		for (Settlement st : s.settlements()) advise(roe, st, now);
	}

	private void announce(RoeServer roe, Event e) {
		SimState s = roe.state();
		Optional<Settlement> st = settlementOf(s, e);
		if (st.isEmpty()) return;
		Settlement settlement = st.get();
		String why = " (/roe why " + e.id() + ")";
		switch (e.type()) {
			case EventTypes.RAID_SIGHTED -> {
				Raid raid = raidOf(s, e).orElse(null);
				if (raid == null) return;
				long hours = Math.max(1, (raid.arriveTime() - e.time()) / GameTime.TICKS_PER_HOUR);
				speak(roe, settlement, "story.raid_sighted", why, direction(settlement.centre(), raid.origin()), raid.size(), hours);
				lightSmoke(roe, raid.origin());
			}
			case EventTypes.RAID_ARRIVED -> speak(roe, settlement, "story.raid_arrived", why);
			case EventTypes.RAID_ENDED -> {
				String outcome = effect(e, "outcome").orElse("");
				speak(roe, settlement, outcome.equals("plundered") ? "story.raid_plundered" : "story.raid_driven_off", why);
			}
			case EventTypes.WINTER_WARNING -> {
				int need = Integer.parseInt(effect(e, "food_needed").orElse("0")) / Balance.QUARTERS_PER_MEAL;
				int have = Integer.parseInt(effect(e, "food_stored").orElse("0")) / Balance.QUARTERS_PER_MEAL;
				speak(roe, settlement, "story.winter_warning", why, need, have);
			}
			case EventTypes.CHARACTER_DIED -> tell(roe, settlement, Component.translatable("story.died", nameOf(s, e)).append(why).withStyle(ChatFormatting.RED));
			case EventTypes.CHARACTER_LEFT -> tell(roe, settlement, Component.translatable("story.left", nameOf(s, e)).append(why).withStyle(ChatFormatting.GOLD));
			case EventTypes.SEASON_CHANGED -> {
				if (Calendar.season(e.time()) == io.github.bl4z3es.riseofempires.core.sim.Season.WINTER) {
					speak(roe, settlement, "story.winter_came", why);
				}
			}
			case EventTypes.ACT_ONE_ENDED -> actOneEnded(roe, settlement, e);
			default -> { }
		}
	}

	private void actOneEnded(RoeServer roe, Settlement st, Event ended) {
		SimState s = roe.state();
		boolean survived = effect(ended, "outcome").orElse("").equals("SURVIVED");
		tell(roe, st, Component.translatable(survived ? "story.act_one_survived" : "story.act_one_lost", st.name())
				.withStyle(survived ? ChatFormatting.GREEN : ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		int missed = 0;
		for (Event e : ActOne.explain(s, st.id())) {
			switch (e.type()) {
				case EventTypes.CHARACTER_DIED -> tell(roe, st, Component.translatable("story.why.died", nameOf(s, e), Calendar.day(e.time()) + 1, e.id()));
				case EventTypes.CHARACTER_LEFT -> tell(roe, st, Component.translatable("story.why.left", nameOf(s, e), Calendar.day(e.time()) + 1, e.id()));
				case EventTypes.GOODS_STOLEN -> tell(roe, st, Component.translatable("story.why.stolen", Calendar.day(e.time()) + 1, e.id()));
				case EventTypes.WINTER_WARNING -> tell(roe, st, Component.translatable("story.why.warned",
						Calendar.day(e.time()) + 1,
						Integer.parseInt(effect(e, "food_needed").orElse("0")) / Balance.QUARTERS_PER_MEAL,
						Integer.parseInt(effect(e, "food_stored").orElse("0")) / Balance.QUARTERS_PER_MEAL, e.id()));
				case EventTypes.MEAL_MISSED -> missed++;
				default -> { }
			}
		}
		if (missed > 0) tell(roe, st, Component.translatable("story.why.missed", missed));
		if (survived) tell(roe, st, Component.translatable("story.why.alive", s.residents(st.id()).size(), st.population().size()));
		tell(roe, st, Component.translatable("story.why.more", ended.id()).withStyle(ChatFormatting.GRAY));
	}

	private void advise(RoeServer roe, Settlement st, long now) {
		ServerPlayer founder = founderNear(roe, st);
		if (founder == null) return;
		Optional<Advice.Line> line = Advice.next(roe.state(), st.id(), now, roe.content().food().totalQuarters(roe.state().stockpile(st.id())));
		if (line.isEmpty()) return;
		String key = line.get().key() + line.get().args();
		boolean changed = !key.equals(lastAdvice.get(st.id()));
		long since = now - lastAdviceTime.getOrDefault(st.id(), Long.MIN_VALUE / 2);
		if (!changed && since < ADVICE_INTERVAL_TICKS) return;
		lastAdvice.put(st.id(), key);
		lastAdviceTime.put(st.id(), now);
		speak(roe, st, line.get().key(), "", line.get().args().toArray());
	}

	/** A line spoken by the first living resident, to the founder if they are in or near the camp. */
	private void speak(RoeServer roe, Settlement st, String key, String why, Object... args) {
		List<Character> people = roe.state().residents(st.id());
		if (people.isEmpty()) return;
		Component line = Component.literal("<" + people.get(0).name() + "> ").append(Component.translatable(key, args)).append(why);
		ServerPlayer founder = founder(roe, st);
		if (founder != null) founder.sendSystemMessage(line);
	}

	private static void tell(RoeServer roe, Settlement st, Component line) {
		ServerPlayer founder = founder(roe, st);
		if (founder != null) founder.sendSystemMessage(line);
	}

	private static ServerPlayer founder(RoeServer roe, Settlement st) {
		try {
			return roe.server().getPlayerList().getPlayer(UUID.fromString(st.founder()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static ServerPlayer founderNear(RoeServer roe, Settlement st) {
		ServerPlayer p = founder(roe, st);
		if (p == null) return null;
		Place here = RoeServer.place((ServerLevel) p.level(), p.blockPosition());
		long r = st.claimRadius() + 16L;
		return here.horizontalDistanceSq(st.centre()) <= r * r ? p : null;
	}

	/** Smoke on the horizon: a campfire where the raiders gather, set when nobody is close enough to see it placed. */
	private static void lightSmoke(RoeServer roe, Place origin) {
		ServerLevel level = roe.level(origin);
		BlockPos at = RoeServer.pos(origin);
		int y = level.isLoaded(at) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) : origin.y();
		DeferredEdits.INSTANCE.add(new DeferredEdits.Edit(origin.dimension(), origin.x(), y, origin.z(), "minecraft:campfire[lit=true]", 0));
	}

	private static String direction(Place from, Place to) {
		double angle = Math.toDegrees(Math.atan2(to.z() - from.z(), to.x() - from.x()));
		String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
		return names[(int) Math.floorMod(Math.round(angle / 45.0), 8L)];
	}

	private static Optional<Settlement> settlementOf(SimState s, Event e) {
		for (Ref r : e.actors()) {
			if (r.kind().equals(Ref.SETTLEMENT)) return s.settlement(Long.parseLong(r.id()));
		}
		for (Ref r : e.actors()) {
			if (r.kind().equals(Ref.RAID)) return s.raid(Long.parseLong(r.id())).flatMap(raid -> s.settlement(raid.settlementId()));
			if (r.kind().equals(Ref.CHARACTER)) {
				Optional<Character> c = s.character(Long.parseLong(r.id()));
				if (c.isPresent() && c.get().settlementId() > 0) return s.settlement(c.get().settlementId());
			}
		}
		return Optional.empty();
	}

	private static Optional<Raid> raidOf(SimState s, Event e) {
		for (Ref r : e.actors()) if (r.kind().equals(Ref.RAID)) return s.raid(Long.parseLong(r.id()));
		return Optional.empty();
	}

	private static String nameOf(SimState s, Event e) {
		for (Ref r : e.actors()) {
			if (r.kind().equals(Ref.CHARACTER)) return s.character(Long.parseLong(r.id())).map(Character::name).orElse("Someone");
		}
		return "Someone";
	}

	private static Optional<String> effect(Event e, String what) {
		return e.effects().stream().filter(f -> f.what().equals(what)).map(Effect::detail).findFirst();
	}
}
