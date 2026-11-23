package io.github.bl4z3es.riseofempires.core.model;

/** Something a character remembers, tied to the event it came from. Event 0 means it predates the Ledger. */
public record Memory(long eventId, String text) {}
