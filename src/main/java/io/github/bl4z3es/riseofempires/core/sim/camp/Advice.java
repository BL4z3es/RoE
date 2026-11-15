package io.github.bl4z3es.riseofempires.core.sim.camp;

/**
 * One thing a survivor tells the player the camp needs.
 *
 * @param speakerId   the character saying it
 * @param priority    higher is more urgent
 * @param basedOn     id of the event this advice rests on, or 0 when it rests on the camp's state
 * @param topic       a stable key, for example {@code food}, so the game can avoid repeating itself
 * @param text        the line, in the speaker's voice
 */
public record Advice(String speakerId, int priority, long basedOn, String topic, String text) {}
