/**
 * The Adjudicator client (Milestone 3). Sends open-ended actions to a language model.
 *
 * <p>Async only, never on the server tick (rule E7). The model returns a proposal; the engine
 * validates and clamps it and applies it as ordinary Ledger events (rules E14 and E15). Every
 * call has a rules-only fallback (rule E16).
 */
package io.github.bl4z3es.riseofempires.ai;
