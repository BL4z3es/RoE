# Tasks

Work top to bottom. Take the first unchecked task. Do not start a milestone while the one before it has open tasks (rule E22).

**How to mark progress**

- `[ ]` not started, `[~]` in progress, `[x]` done, `[!]` blocked and waiting on the owner.
- When a task is done, tick it and add one indented line starting with `Note:` saying what was built.
- Each milestone ends with a playtest. Record what the players said under the milestone.

**Milestone map**

```mermaid
flowchart LR
    M0["M0 Setup"] --> M1["M1 The First Winter"]
    M1 --> M2["M2 Hamlet"]
    M2 --> M3["M3 Heartland"]
    M3 --> M4["M4 The Realms"]
    M4 --> M5["M5 The Crown"]
    M5 --> L["Later: Open World, Server"]
```

---

## M0 · Setup

Goal: an empty mod that builds, runs and has a place for tests.

- [x] **M0-1 Pin versions.** Ask the owner for the Java group name and confirm the mod id. Look up the current stable Minecraft, Fabric Loader, Fabric API, Loom and Gradle versions on the Fabric develop page. Write them to `gradle.properties` and to `CONTEXT.md` section 4.
  - Done when: the versions are recorded and the owner has confirmed the group and mod id.
  - Note: Pinned MC 26.3, Loader 0.20.1, Fabric API 0.166.2+26.3, Loom 1.19.4 (the template floats `1.19-SNAPSHOT`; pinned exact instead), Gradle 9.8.2, Java 25. Group `io.github.bl4z3es`, mod id `riseofempires`, JUnit 5 approved. Owner: `RULES.md` E20 still tags test-only libraries `PROPOSED`; you decided it, so that tag is yours to update. 26.4 is at snapshot 10 and will likely release soon; upgrading is a separate task, not automatic.
- [ ] **M0-2 Generate the project.** Create a Fabric mod project from the official template for the pinned version. Split client and common source sets.
  - Done when: `./gradlew build` prints `BUILD SUCCESSFUL` and a jar is in `build/libs/`.
- [ ] **M0-3 Create the package layout** from `CLAUDE.md`: `core/`, `mc/`, `ai/`, with a `package-info.java` in each stating what belongs there.
  - Done when: the layout exists and `core/` compiles with no Minecraft imports.
- [ ] **M0-4 Log on start.** The mod logs `[riseofempires] initialised` on server start.
  - Done when: `./gradlew runClient` and `./gradlew runServer` both show the line.
- [ ] **M0-5 Test harness.** Add pure-Java tests for `core/` and in-world game tests. Check with the owner first that a test-only library is acceptable (open question 7).
  - Done when: `./gradlew test` runs one passing sample test and the game-test task runs one passing sample test. Update the command names in `CLAUDE.md` if they differ.
- [ ] **M0-6 Guard the core.** Add a build check that fails if anything under `core/` imports a Minecraft class.
  - Done when: adding such an import makes `./gradlew build` fail with a clear message.
- [ ] **M0-7 Constants home.** Create the single config class for balance numbers and load the values from `CONTEXT.md` section 5 into it.
  - Done when: every row of the numbers table that M1 uses exists as a named constant.

---

## M1 · The First Winter

Goal: a complete, polished 3–5 hour slice. The player finds the Charter, founds a camp, takes in three survivors, builds three starter buildings with them, fights off one raid and tries to get through the first winter.

Not in M1: other nations, diplomacy, research, model calls, money, freehand anchors, the Ghosted tier.

### Foundation

- [ ] **M1-01 Ledger core.** The `Event` record from `CONTEXT.md` 3.11, an append-only log, cause links, and a query for "what caused this".
  - Done when: tests show an event chain can be written, reloaded and walked backwards from any event to its roots.
- [ ] **M1-02 Persistence.** Save and load the Ledger and all `core/model` records through Minecraft's saved-data system. The event log is an append-only file per world.
  - Done when: a game test saves a world, reloads it and finds identical state.
- [ ] **M1-03 Calendar and seasons.** In-game days map to seasons and years. A `SeasonChanged` event fires at each boundary.
  - Done when: tests cover day-to-season mapping and a season change appears in the Ledger.
- [ ] **M1-04 `/roe` debug commands.** `/roe time`, `/roe events [n]`, `/roe why <eventId>`.
  - Done when: `/roe why` prints a cause chain in-game.

### People and places

- [ ] **M1-05 Character records.** Name, trait, want, profession, memories, needs. Persistent and independent of any entity.
  - Done when: tests create, save and reload a character with every field intact.
- [ ] **M1-06 Settlement record and claim.** A settlement has a centre, a claim radius, a population list and a stockpile reference.
  - Done when: `/roe settlement` shows the settlement the player is standing in.
- [ ] **M1-07 Embodied characters.** A server-side entity bound to a character record. It walks, carries items and uses containers. When it unloads, the record stays.
  - Done when: a game test spawns a character out of sight, has it walk a path and place an item in a chest, then unloads and reloads it with the same identity.
- [ ] **M1-08 Seasonal farming.** Crop growth obeys the season: none in winter, faster in spring.
  - Done when: a game test shows no outdoor growth in winter.

### The opening

- [ ] **M1-09 The Hollow Ruin.** A hand-built structure placed by world generation near spawn in the starting region, guarded by weak raiders, holding the Charter. Build it to the L1 quality bar.
  - Done when: a fresh world has one reachable Hollow Ruin within a set distance of spawn, and the owner has approved how it looks.
- [ ] **M1-10 The Charter.** An item found in the ruin. Placing it on a lectern founds the camp: a `SettlementFounded` event, a claim, a tent and a campfire.
  - Done when: founding works once per player, is recorded in the Ledger and survives a reload.
- [ ] **M1-11 The first night.** On the first night after founding, three survivors walk in from beyond sight and ask to stay. Each has a name, a trait, a want and a memory.
  - Done when: they arrive on foot from out of view, never spawn in sight, and their arrival has a cause in the Ledger.

### The loop

- [ ] **M1-12 Stockpile.** A Granary Seal marks a storage building. The stockpile is the real contents of its containers.
  - Done when: adding or removing items by hand changes what `/roe stockpile` reports.
- [ ] **M1-13 Blueprints.** The player places a ghost outline. Builders fetch materials from the stockpile and build it block by block. Ship three: farm, stable, bunkhouse, in the Hollowcrown style.
  - Done when: all three can be built start to finish by survivors while the player watches, and the owner has approved how the finished buildings look.
- [ ] **M1-14 Jobs.** Farmer (till, plant, harvest, deposit), builder (M1-13), gatherer (wood and stone to the stockpile). The player assigns jobs by interacting with a survivor.
  - Done when: an assigned farmer completes a full harvest-and-deposit cycle with no help.
- [ ] **M1-15 Needs: food and rest.** People eat twice a day from the stockpile and sleep in a bed under a roof. Unmet needs lower output, then cause illness, then death or leaving. Each step is a Ledger event.
  - Done when: tests cover the full decline, and in-game an unfed survivor visibly weakens and then leaves or dies with a traceable cause.
- [ ] **M1-16 Ledger tier.** When the settlement unloads, its people keep working as records, updated once per in-game hour, using the same rates as the embodied jobs.
  - Done when: leaving for a full in-game day and coming back shows crops harvested, food eaten and the stockpile changed.
- [ ] **M1-17 Deferred edits.** Off-camera block changes queue per chunk and apply when the chunk loads.
  - Done when: a builder finishes a blueprint while the player is away and the building is standing on return.
- [ ] **M1-18 Tier-invariance test.** Run the same settlement for ten in-game days in the Embodied tier and in the Ledger tier.
  - Done when: food produced and eaten agree within a tolerance written in the test, and the test runs in the build.

### The test of the act

- [ ] **M1-19 Standing, first version.** Compute Standing from population and territory only.
  - Done when: `/roe standing` shows the score and its parts.
- [ ] **M1-20 Barbarian raid.** One T1 raid sized from Standing: leather armour, diamond axes, very low health. It is announced first (smoke on the horizon, a warning from a survivor), walks in from the Wilds and goes for the food store.
  - Done when: the raid never appears in view, the player has time to prepare, and the outcome is in the Ledger.
- [ ] **M1-21 The first winter.** Winter arrives on the calendar. A survivor warns the player one season ahead. Getting through with at least one survivor alive completes Act I.
  - Done when: both endings work (the camp survives, or the camp is lost) and each tells the player why, from the Ledger.
- [ ] **M1-22 Player guidance.** The survivors tell the player what the camp needs, in character, from template text. No tutorial pop-ups.
  - Done when: a new player can reach winter without outside instructions.

### Finish

- [ ] **M1-23 Performance check.** Measure server tick time with the camp loaded and with it unloaded. Record the hardware, the method and the results here.
  - Done when: the numbers are written under this task.
- [ ] **M1-24 Polish pass.** Fix anything that pops in, teleports, idles with no reason or looks unfinished.
  - Done when: every line of the quality bar in `RULES.md` section 4 holds.
- [ ] **M1-25 Playtest.** Three people who did not build it play from spawn to the end of winter.
  - Done when: what they said is written below and the owner has decided what to change before M2.

**M1 playtest notes:** none yet.

---

## M2 · Hamlet

Goal: about 10 hours. The camp becomes a hamlet with a neighbour who plays by the same rules.

Tasks are listed at outline level. Break each into M1-sized tasks when M1 is finished, using what the playtest showed.

- [ ] **M2-A Anchors and freehand building.** Room detection, the eight anchor blocks, building quality, save-as-blueprint.
- [ ] **M2-B Shelter and safety needs.** Housing quality and nearby danger now matter.
- [ ] **M2-C Migration pool and Appeal.** A finite regional population that moves by Appeal each season.
- [ ] **M2-D One AI warlord.** A neighbouring heartland settlement running the same simulation (rule E1), with its own stockpile and people.
- [ ] **M2-E Relations and grievances.** Opinion and grievances tied to events. The labour-drain chain from the owner's example must work end to end.
- [ ] **M2-F Template decrees.** Book and Quill on the Lectern of Office: tax, rations, conscription, gates, curfew.
- [ ] **M2-G Loyalty, Fear, Memory.** Harsh acts are allowed and remembered (law L4).
- [ ] **M2-H Research: Ash and Iron eras.** Scholars, Knowledge, ruin salvage, the Masonry breakthrough.
- [ ] **M2-I T2 threats.** Organised warbands with a captain. Standing gains army and research terms.
- [ ] **M2-J Settlement ranks** up to Town. More blueprints.
- [ ] **M2-K Act II calamity.** Drought or blight, with omens. Needs the owner's choice (open question 2).
- [ ] **M2-L Performance check, polish, playtest.**

**M2 playtest notes:** none yet.

---

## M3 · Heartland

Goal: about 25 hours. Diplomacy, trade and sieges across the heartland. The model arrives here.

- [ ] **M3-A The Adjudicator.** Async client, JSON contract, clamps, rules-only fallback, config for endpoint and key (rules E14–E19).
- [ ] **M3-B Model-invariance test.** Same seed, two models, same economic and territorial outcome.
- [ ] **M3-C Cost measurement.** Count calls and tokens over a real session and record them. Closes the open cost question.
- [ ] **M3-D Custom decrees and letters.** Free-text decrees. Letters carried by couriers who can be intercepted.
- [ ] **M3-E Agreements.** Trade pact, non-aggression, border treaty, vassalage, tribute.
- [ ] **M3-F Caravans, roads and Marks.** Physical trade, the Mint, wages and taxes.
- [ ] **M3-G Renown and rumour.** News travels along roads with delay.
- [ ] **M3-H Ghosted tier.** The middle level of detail.
- [ ] **M3-I T3 threats and sieges.** Foreign armies, siege equipment, starvation sieges, occupation.
- [ ] **M3-J The Chronicle.** An in-game book that reads the Ledger.
- [ ] **M3-K Research: Guild era.**
- [ ] **M3-L Act III.** The Crown Seat and its calamity.
- [ ] **M3-M Performance check, polish, playtest.**

**M3 playtest notes:** none yet.

---

## M4 · The Realms

Goal: 50 hours and up. The full Known World.

- [ ] **M4-A The Age Before.** History simulation at world creation, placing real towns, ruins and roads.
- [ ] **M4-B Realm generation.** The Known World layout and the three outer realms, each with its own blueprint set.
- [ ] **M4-C Rulers as characters.** Traits, goals and memory for each realm's ruler.
- [ ] **M4-D Battle engine.** Formations, the War Banner, the rolling front, the off-camera battle calculator.
- [ ] **M4-E Battle benchmarks.** 50 against 50, then 100, 250, 500, 1,000. Record tick time and client frame rate at each step. The owner sets the committed battle size from these results (open question 8).
- [ ] **M4-F The Nether front.**
- [ ] **M4-G Research: Crown era.**
- [ ] **M4-H Alliance and marriage agreements.**
- [ ] **M4-I Performance check, polish, playtest.**

**M4 playtest notes:** none yet.

---

## M5 · The Crown

Goal: the story can be finished, and play continues after it.

- [ ] **M5-A Act IV.** T4 threats and the win condition. Needs the owner's choice (open question 3).
- [ ] **M5-B Epilogue.** Rebellions, pretenders and Wild hordes after the story ends.
- [ ] **M5-C Research: Imperial era and repeatable research.** Check law L6: growth must not flatten.
- [ ] **M5-D Full playthrough test, polish, playtest.**

**M5 playtest notes:** none yet.

---

## Later

Out of scope until the owner says otherwise.

- Open World mode.
- Server mode.

---

## Ideas

Things that came up while working and are not part of any task. Add here in place of building them (rule E21).

- None yet.
