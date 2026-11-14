package io.github.bl4z3es.riseofempires.core.ledger;

import io.github.bl4z3es.riseofempires.core.Wire;
import java.util.List;

/**
 * One thing that happened (CONTEXT.md 3.11). Events are immutable and are only created by
 * {@link Ledger#append}. {@code summary} is a plain sentence the player can read in
 * {@code /roe why} and in the Chronicle.
 *
 * @param hour in-game hours since the world began (24 per Minecraft day)
 */
public record Event(
        long id,
        long hour,
        Place place,
        String type,
        List<Ref> actors,
        List<Long> causes,
        List<Effect> effects,
        List<Ref> witnesses,
        String summary) {

    public Event {
        actors = List.copyOf(actors);
        causes = List.copyOf(causes);
        effects = List.copyOf(effects);
        witnesses = List.copyOf(witnesses);
    }

    public String encode() {
        return Wire.line(
                id, hour, place.encode(), type,
                Wire.list(actors.stream().map(Ref::toString).toList()),
                Wire.list(causes.stream().map(String::valueOf).toList()),
                Wire.list(effects.stream().map(Effect::encode).toList()),
                Wire.list(witnesses.stream().map(Ref::toString).toList()),
                summary);
    }

    public static Event decode(String line) {
        String[] f = Wire.fields(line);
        return new Event(
                Long.parseLong(f[0]), Long.parseLong(f[1]), Place.decode(f[2]), f[3],
                Wire.unlist(f[4]).stream().map(Ref::parse).toList(),
                Wire.unlist(f[5]).stream().map(Long::parseLong).toList(),
                Wire.unlist(f[6]).stream().map(Effect::decode).toList(),
                Wire.unlist(f[7]).stream().map(Ref::parse).toList(),
                f[8]);
    }
}
