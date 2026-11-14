package io.github.bl4z3es.riseofempires.core.model;

import io.github.bl4z3es.riseofempires.core.Wire;
import io.github.bl4z3es.riseofempires.core.ledger.Place;
import java.util.ArrayList;
import java.util.List;

/**
 * A place people live. Its goods are the stockpile with id {@code stockpileId}.
 *
 * @param rank one of {@link #RANKS}
 */
public record Settlement(
        String id,
        String name,
        String nationId,
        Place centre,
        int claimRadius,
        List<String> residents,
        String stockpileId,
        String rank) {

    public static final List<String> RANKS = List.of("camp", "hamlet", "village", "town", "city", "seat");

    public Settlement {
        residents = List.copyOf(residents);
    }

    public boolean claims(Place place) {
        return centre.distanceTo(place) <= claimRadius;
    }

    public Settlement withResident(String characterId) {
        if (residents.contains(characterId)) return this;
        List<String> r = new ArrayList<>(residents);
        r.add(characterId);
        return new Settlement(id, name, nationId, centre, claimRadius, r, stockpileId, rank);
    }

    public Settlement withoutResident(String characterId) {
        List<String> r = new ArrayList<>(residents);
        r.remove(characterId);
        return new Settlement(id, name, nationId, centre, claimRadius, r, stockpileId, rank);
    }

    public Settlement withRank(String r) {
        return new Settlement(id, name, nationId, centre, claimRadius, residents, stockpileId, r);
    }

    public Settlement withClaimRadius(int radius) {
        return new Settlement(id, name, nationId, centre, radius, residents, stockpileId, rank);
    }

    public Settlement withNation(String nation) {
        return new Settlement(id, name, nation, centre, claimRadius, residents, stockpileId, rank);
    }

    public Settlement withName(String n) {
        return new Settlement(id, n, nationId, centre, claimRadius, residents, stockpileId, rank);
    }

    public String encode() {
        return Wire.line(id, name, nationId, centre.encode(), claimRadius, Wire.list(residents), stockpileId, rank);
    }

    public static Settlement decode(String s) {
        String[] f = Wire.fields(s);
        return new Settlement(f[0], f[1], f[2], Place.decode(f[3]), Integer.parseInt(f[4]),
                Wire.unlist(f[5]), f[6], f[7]);
    }
}
