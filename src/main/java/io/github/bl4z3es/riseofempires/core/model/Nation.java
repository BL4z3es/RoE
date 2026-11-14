package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.Wire;

/**
 * A realm, warlord band or the player's own domain. AI nations and the player's nation are the
 * same record and run the same rules (rule E1).
 *
 * @param rulerId       the ruling character's id, or "" when the ruler is a player
 * @param playerId      the owning player's id, or "" for an AI nation
 * @param realm         realm data id, for example "hollowcrown"; "" for the Wilds
 */
public record Nation(String id, String name, String rulerId, String playerId, String realm) {
    public boolean playerRuled() {
        return !playerId.isEmpty();
    }

    public String encode() {
        return Wire.line(id, name, rulerId, playerId, realm);
    }

    public static Nation decode(String s) {
        String[] f = Wire.fields(s);
        return new Nation(f[0], f[1], f[2], f[3], f[4]);
    }
}
