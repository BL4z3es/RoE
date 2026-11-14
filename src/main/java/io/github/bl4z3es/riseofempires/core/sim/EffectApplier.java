package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.model.Settlement;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Turns effects into state changes. This is the only rule code allowed to call the writers on
 * {@link WorldState}. The built-in kinds are the constants in {@link Effects}.
 */
public final class EffectApplier {
    private final Map<String, BiConsumer<WorldState, Effect>> handlers = new HashMap<>();

    public EffectApplier() {
        register(Effects.STAT_ADD, (s, e) -> s.setStat(e.target(), e.key(), s.stat(e.target(), e.key()) + e.amount()));
        register(Effects.STAT_SET, (s, e) -> s.setStat(e.target(), e.key(), e.amount()));
        register(Effects.TAG_SET, (s, e) -> s.setTag(e.target(), e.key(), e.text()));
        register(Effects.STOCK_ADD, (s, e) ->
                s.putStockpile(s.stockpile(e.target().id()).plus(e.key(), (int) Math.round(e.amount()))));
        register(Effects.CHARACTER_PUT, (s, e) -> s.putCharacter(Character.decode(e.text())));
        register(Effects.CHARACTER_SET, (s, e) -> {
            Character c = s.character(e.target().id());
            if (c == null) return;
            switch (e.key()) {
                case "profession" -> s.putCharacter(c.withProfession(e.text()));
                case "status" -> s.putCharacter(c.withStatus(Character.Status.valueOf(e.text())));
                default -> throw new IllegalArgumentException("Unknown character field " + e.key());
            }
        });
        register(Effects.CHARACTER_REMEMBER, (s, e) -> {
            Character c = s.character(e.target().id());
            if (c != null) s.putCharacter(c.withMemory(e.text()));
        });
        register(Effects.SETTLEMENT_PUT, (s, e) -> s.putSettlement(Settlement.decode(e.text())));
        register(Effects.SETTLEMENT_SET, (s, e) -> {
            Settlement t = s.settlement(e.target().id());
            if (t == null) return;
            switch (e.key()) {
                case "name" -> s.putSettlement(t.withName(e.text()));
                case "rank" -> s.putSettlement(t.withRank(e.text()));
                case "nation" -> s.putSettlement(t.withNation(e.text()));
                case "claimRadius" -> s.putSettlement(t.withClaimRadius(Integer.parseInt(e.text())));
                default -> throw new IllegalArgumentException("Unknown settlement field " + e.key());
            }
        });
        register(Effects.RESIDENT_ADD, (s, e) -> {
            Settlement t = s.settlement(e.target().id());
            Character c = s.character(e.text());
            if (t == null || c == null) return;
            Settlement old = s.settlement(c.settlementId());
            if (old != null && !old.id().equals(t.id())) s.putSettlement(old.withoutResident(c.id()));
            s.putSettlement(t.withResident(c.id()));
            s.putCharacter(c.withSettlement(t.id()));
        });
        register(Effects.RESIDENT_REMOVE, (s, e) -> {
            Settlement t = s.settlement(e.target().id());
            Character c = s.character(e.text());
            if (t != null) s.putSettlement(t.withoutResident(e.text()));
            if (c != null && c.settlementId().equals(e.target().id())) s.putCharacter(c.withSettlement(""));
        });
        register(Effects.NATION_PUT, (s, e) -> s.putNation(Nation.decode(e.text())));
    }

    /** Adds a handler for a new effect kind. Registering a kind twice is an error. */
    public void register(String kind, BiConsumer<WorldState, Effect> handler) {
        if (handlers.putIfAbsent(kind, handler) != null) {
            throw new IllegalStateException("Effect kind registered twice: " + kind);
        }
    }

    public boolean knows(String kind) {
        return handlers.containsKey(kind);
    }

    public void apply(WorldState state, Effect effect) {
        BiConsumer<WorldState, Effect> handler = handlers.get(effect.kind());
        if (handler == null) throw new IllegalArgumentException("No handler for effect kind " + effect.kind());
        handler.accept(state, effect);
    }
}
