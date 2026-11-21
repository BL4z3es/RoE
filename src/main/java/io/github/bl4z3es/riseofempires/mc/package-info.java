/**
 * Minecraft adapters. Translates between the world and {@code core}.
 *
 * <p>Reads world state into core inputs and turns core events into world changes: embodied
 * characters, anchor blocks, structures, saved data, deferred chunk edits and {@code /roe}
 * commands. No game rule is decided here; if a class here computes an outcome, that logic
 * belongs in {@code core}.
 */
package io.github.bl4z3es.riseofempires.mc;
