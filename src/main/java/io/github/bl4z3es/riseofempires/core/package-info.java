/**
 * The simulation. Plain Java only: no Minecraft, Fabric or network imports (rule E5).
 *
 * <p>All game rules live here: the Ledger, the calendar, needs, production, migration, Standing,
 * and the records for characters, settlements, nations and stockpiles. State changes only by
 * applying a Ledger event (rule E2). Randomness comes from a seeded source passed in by the
 * caller, and nothing reads the wall clock (rule E4). Every rule here has a pure-Java test.
 */
package io.github.bl4z3es.riseofempires.core;
