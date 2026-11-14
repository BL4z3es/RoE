package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.Wire;
import java.util.ArrayList;
import java.util.List;

/**
 * A person. The record exists whether or not a body is loaded in the world. Changing numbers
 * such as hunger or loyalty live in the stat store of {@code WorldState}, keyed by this
 * character's ref, so that new systems can add values without changing this record.
 *
 * @param settlementId the settlement they live in, or "" for none
 * @param profession   one of {@link Professions}, or "" for unassigned
 */
public record Character(
        String id,
        String name,
        String trait,
        String want,
        String profession,
        List<String> memories,
        String settlementId,
        Status status) {

    public enum Status { ALIVE, DEAD, LEFT }

    public Character {
        memories = List.copyOf(memories);
    }

    public boolean alive() {
        return status == Status.ALIVE;
    }

    public Character withProfession(String p) {
        return new Character(id, name, trait, want, p, memories, settlementId, status);
    }

    public Character withSettlement(String s) {
        return new Character(id, name, trait, want, profession, memories, s, status);
    }

    public Character withStatus(Status s) {
        return new Character(id, name, trait, want, profession, memories, settlementId, s);
    }

    public Character withMemory(String memory) {
        List<String> m = new ArrayList<>(memories);
        m.add(memory);
        return new Character(id, name, trait, want, profession, m, settlementId, status);
    }

    public String encode() {
        return Wire.line(id, name, trait, want, profession, Wire.list(memories), settlementId, status);
    }

    public static Character decode(String s) {
        String[] f = Wire.fields(s);
        return new Character(f[0], f[1], f[2], f[3], f[4], Wire.unlist(f[5]), f[6], Status.valueOf(f[7]));
    }
}
