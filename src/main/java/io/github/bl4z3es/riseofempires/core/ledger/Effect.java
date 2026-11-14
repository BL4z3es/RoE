package io.github.bl4z3es.riseofempires.core.ledger;

import io.github.bl4z3es.riseofempires.core.Wire;

/**
 * One state change carried by an event. Effects are plain data so they can be saved, replayed
 * and proposed by the Adjudicator. What an effect does is decided by the handler registered for
 * its {@code kind} in {@code core.sim.EffectApplier}. The built-in kinds are listed there.
 *
 * @param kind   handler name, for example {@code stat.add}
 * @param target what is changed
 * @param key    which value on the target (a stat name, an item id, a field name); may be empty
 * @param amount numeric part; 0 when unused
 * @param text   text part (an encoded record, a new field value); empty when unused
 */
public record Effect(String kind, Ref target, String key, double amount, String text) {
    public static Effect of(String kind, Ref target, String key, double amount) {
        return new Effect(kind, target, key, amount, "");
    }

    public static Effect of(String kind, Ref target, String key, String text) {
        return new Effect(kind, target, key, 0, text);
    }

    public String encode() {
        return Wire.line(kind, target, key, amount, text);
    }

    public static Effect decode(String s) {
        String[] f = Wire.fields(s);
        return new Effect(f[0], Ref.parse(f[1]), f[2], Double.parseDouble(f[3]), f[4]);
    }
}
