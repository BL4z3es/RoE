package io.github.bl4z3es.riseofempires.core.sim;

import io.github.bl4z3es.riseofempires.core.ledger.Effect;
import io.github.bl4z3es.riseofempires.core.ledger.Ref;
import io.github.bl4z3es.riseofempires.core.model.Character;
import io.github.bl4z3es.riseofempires.core.model.Nation;
import io.github.bl4z3es.riseofempires.core.model.Settlement;

/**
 * Factory methods for the built-in effect kinds. Use these in place of writing kind strings by
 * hand. A system that needs a kind of its own registers a handler with
 * {@link EffectApplier#register} and keeps its factory methods in its own class.
 */
public final class Effects {
    private Effects() {}

    public static final String STAT_ADD = "stat.add";
    public static final String STAT_SET = "stat.set";
    public static final String TAG_SET = "tag.set";
    public static final String STOCK_ADD = "stock.add";
    public static final String CHARACTER_PUT = "character.put";
    public static final String CHARACTER_SET = "character.set";
    public static final String CHARACTER_REMEMBER = "character.remember";
    public static final String SETTLEMENT_PUT = "settlement.put";
    public static final String SETTLEMENT_SET = "settlement.set";
    public static final String RESIDENT_ADD = "settlement.resident.add";
    public static final String RESIDENT_REMOVE = "settlement.resident.remove";
    public static final String NATION_PUT = "nation.put";

    /** Adds {@code delta} (may be negative) to a stat. */
    public static Effect addStat(Ref target, String stat, double delta) {
        return Effect.of(STAT_ADD, target, stat, delta);
    }

    public static Effect setStat(Ref target, String stat, double value) {
        return Effect.of(STAT_SET, target, stat, value);
    }

    /** Sets a text tag. An empty value removes it. */
    public static Effect setTag(Ref target, String tag, String value) {
        return Effect.of(TAG_SET, target, tag, value);
    }

    /** Adds (or with a negative amount removes) whole items in a stockpile. */
    public static Effect addStock(String stockpileId, String item, int amount) {
        return Effect.of(STOCK_ADD, Ref.stockpile(stockpileId), item, amount);
    }

    /** Creates a character, or replaces the record with the same id. */
    public static Effect putCharacter(Character c) {
        return Effect.of(CHARACTER_PUT, Ref.character(c.id()), "", c.encode());
    }

    public static Effect setProfession(String characterId, String profession) {
        return Effect.of(CHARACTER_SET, Ref.character(characterId), "profession", profession);
    }

    public static Effect setStatus(String characterId, Character.Status status) {
        return Effect.of(CHARACTER_SET, Ref.character(characterId), "status", status.name());
    }

    public static Effect remember(String characterId, String memory) {
        return Effect.of(CHARACTER_REMEMBER, Ref.character(characterId), "", memory);
    }

    public static Effect putSettlement(Settlement s) {
        return Effect.of(SETTLEMENT_PUT, Ref.settlement(s.id()), "", s.encode());
    }

    /** {@code field} is one of: name, rank, nation, claimRadius. */
    public static Effect setSettlement(String settlementId, String field, String value) {
        return Effect.of(SETTLEMENT_SET, Ref.settlement(settlementId), field, value);
    }

    /** Moves a character into a settlement: updates both the settlement and the character. */
    public static Effect addResident(String settlementId, String characterId) {
        return Effect.of(RESIDENT_ADD, Ref.settlement(settlementId), "", characterId);
    }

    /** Removes a character from a settlement's resident list and clears their home. */
    public static Effect removeResident(String settlementId, String characterId) {
        return Effect.of(RESIDENT_REMOVE, Ref.settlement(settlementId), "", characterId);
    }

    public static Effect putNation(Nation n) {
        return Effect.of(NATION_PUT, Ref.nation(n.id()), "", n.encode());
    }
}
