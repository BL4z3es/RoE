# Rules

Two kinds of rule live here. **Design laws** come from the owner and are fixed. **Engineering rules** are how the code obeys them. If a task ever conflicts with a rule, the rule wins and the task gets raised with the owner.

---

## 1. Design laws (owner's hard rules)

### L1. Quality over quantity
The player must always find a finished world waiting for them. A world made of entities summoned in front of the player is a failure.

- Nothing spawns, appears or pops in within the player's view. Raiders walk in from beyond sight. Buildings are built block by block or already exist.
- Structures come from hand-authored modules, in the style of the realm they belong to.
- One finished feature is worth more than three rough ones. A milestone ships only when it feels complete.

### L2. Causality
Every outcome has a cause, and the cause can be found.

- A small policy change can anger a neighbour because it drew away their workers. A winter with no stockpile causes famine.
- No random event happens with no upstream reason. Random rolls are allowed, but what they roll on must come from world state.
- The player must be able to ask why something happened and get a true answer.

### L3. Off-camera magic is allowed
Things the player cannot see do not need to be rendered or fully simulated. A mining crew 1,000 blocks away can be numbers.

- The result must match what would have happened if the player had watched.
- When the player goes to look, the world shows the result.

### L4. No restrictions
The player may do anything. A harsh ruler may kill their own workers for no reason.

- Never block an action because it is cruel, foolish or unfair. Never show "you can't do that" for a moral reason.
- The world answers through L2: fear, loyalty, memory, rumour, revolt.

### L5. Open-ended results go to GenAI
When an action's outcome cannot be decided by a rule, a language model fills the gap.

- The model fills gaps. It does not replace rules that exist.

### L6. Progression never flattens
Growth is linear or exponential, never logarithmic. There is no apparent stopping point.

- One path may give diminishing returns for a while. Another path must then open and restore the gain. Example: a new city yields little, research makes cities cheaper, and building cities is worth it again.
- No hard caps on population, territory, research or army size in design. Performance limits are solved with L3.

---

## 2. Decision ownership

Every design decision in `CONTEXT.md` carries one of three tags.

| Tag | Meaning | Who may change it |
|---|---|---|
| `OWNER` | The owner decided this. | Owner only |
| `PROPOSED` | Claude chose this to fill a gap. It is a starting point. | Owner. Claude may recommend a change with evidence. |
| `OPEN` | Not decided. | Owner. Work that depends on it stops and asks. |

- Never turn a `PROPOSED` or `OPEN` item into `OWNER` without the owner saying so.
- When the owner decides something, record it in the decision log in `CONTEXT.md` with the date.
- Never invent lore, features or numbers and present them as decided.

---

## 3. Engineering rules

### Simulation
- **E1. One ruleset.** The player's settlements and AI nations run the same code with the same costs. AI nations get no free resources and no hidden bonuses.
- **E2. Everything is an event.** State changes only by applying a Ledger event. Every event records its causes, its actors and its effects. Code that changes state directly is a bug.
- **E3. Tier invariance.** A system must give the same expected result in the Embodied, Ghosted and Ledger tiers. Each system that runs in more than one tier has a test that proves this within a stated tolerance.
- **E4. Deterministic core.** `core/` uses a seeded random source passed in by the caller. The same seed and the same inputs give the same outputs. No wall-clock time, no unseeded randomness.
- **E5. Core has no Minecraft imports.** Rules live in `core/` as plain Java. `mc/` translates between the world and the core. This keeps the rules testable without launching the game.
- **E6. World edits made off-camera are deferred.** They go in a per-chunk queue and apply in order when the chunk loads. Never force-load chunks to do simulation work.

### Performance
- **E7. Never block the server tick.** No file, network or model call on the tick thread. Long work is spread across ticks or moved off-thread and handed back as events.
- **E8. Measure before claiming.** No performance figure goes into `CONTEXT.md` or a commit message unless it was measured, with the hardware and the method noted. Large-battle sizes are targets until benchmarked.
- **E9. Budget the tick.** Simulation work has a per-tick time budget. Work that does not fit waits for the next tick.

### Minecraft
- **E10. Server-authoritative.** All simulation runs on the logical server. The client renders and sends input. `./gradlew runServer` must always start cleanly.
- **E11. Content is data.** Blueprints, anchors, research projects, calamities and realm definitions are data-pack files. Adding content should not need a code change.
- **E12. Real items, real blocks.** Goods are Minecraft items in real containers. Off-camera production is reconciled into containers when the area loads.
- **E13. Vanilla stays vanilla.** Do not change vanilla behaviour outside the mod's own systems unless a task says so.

### GenAI (Adjudicator)
- **E14. The model never writes state.** It returns a structured proposal. The engine validates it, clamps it to the limits for its category, and applies it as ordinary Ledger events.
- **E15. The model only moves values the rules already read.** It cannot create items, kill characters, move borders or declare outcomes.
- **E16. The game works with no model.** Every model call has a rules-only fallback. A missing, slow or failing model must never stop play.
- **E17. Characters know only what they could know.** A prompt contains only facts the relevant characters witnessed or were told.
- **E18. Keys and privacy.** The player supplies their own endpoint and key in config. Never log the key. Never send anything to a model endpoint except what the call needs.
- **E19. Outcomes do not depend on the model.** With the same seed, two different models must give the same economic and territorial results. Only the wording may differ, plus effects inside the clamp limits.

### Dependencies and scope
- **E20. No added dependencies.** The mod ships with Fabric Loader and Fabric API only. Model calls use the JDK's HTTP client. Storage uses Minecraft's saved-data system and plain files. Test-only libraries that are not shipped in the jar are allowed (`PROPOSED`).
- **E21. No scope creep.** Build what the current task asks for. Ideas go in the "Ideas" section at the bottom of `TASKS.md`.
- **E22. Milestones are in order.** No work on a later milestone while the current one has open tasks. Open World and Server modes are out of scope until the owner says otherwise.

### Code
- **E23. Records for data, small classes for rules.** Prefer immutable records in `core/model`.
- **E24. Name things by what the player sees.** `Granary`, `Charter`, `Decree`. Avoid internal jargon in anything player-facing.
- **E25. Tunable numbers live in one place.** Every balance constant is named, lives in a config class or data file, and is listed in the numbers table in `CONTEXT.md`. No magic numbers in rule code.
- **E26. Debug commands under `/roe`.** Every system gets a command that shows its state. `/roe why <eventId>` prints an event's cause chain.

### Git
- **E27. One task, one branch, one pull request.** Branch name: `m1-03-character-records`.
- **E28. Commit messages say what changed and which task.** Example: `M1-03: add Character record and saved-data codec`.
- **E29. Never commit** API keys, world saves, or `run/` directories.

---

## 4. Player-facing quality bar

Before a milestone is called finished, each of these must be true.

- A new player understands what to do next without reading outside documentation.
- Every warning the game gives arrives in time to act on it. Raids are announced. Calamities have omens.
- Nothing visibly teleports, pops in or stands idle with no reason.
- Three people who did not build it have played it, and what they said is written down under the milestone in `TASKS.md`.
