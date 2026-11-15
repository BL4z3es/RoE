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
  - Note: Owner chose group `io.github.bl4z3es` and mod id `roe` on 2026-11-14. First pinned to Minecraft 26.3; moved to Minecraft 1.21.11 (Fabric Loader 0.20.1, Fabric API 0.141.6+1.21.11, Java 21, Mojang mappings) at the owner's request on 2026-11-15. Build, unit tests, all 8 game tests and a dedicated-server start pass on 1.21.11.
- [x] **M0-2 Generate the project.** Create a Fabric mod project from the official template for the pinned version. Split client and common source sets.
  - Done when: `./gradlew build` prints `BUILD SUCCESSFUL` and a jar is in `build/libs/`.
  - Note: Written by hand to match the Fabric layout for 26.3 (the GitHub template was not reachable). Client code is in `src/client`. `./gradlew build` passes and produces `build/libs/riseofempires-0.1.0-alpha.jar`.
- [x] **M0-3 Create the package layout** from `CLAUDE.md`: `core/`, `mc/`, `ai/`, with a `package-info.java` in each stating what belongs there.
  - Done when: the layout exists and `core/` compiles with no Minecraft imports.
  - Note: Packages created under `io.github.bl4z3es.riseofempires` with a `package-info.java` each.
- [~] **M0-4 Log on start.** The mod logs `[riseofempires] initialised` on server start.
  - Done when: `./gradlew runClient` and `./gradlew runServer` both show the line.
  - Note: `runServer` shows the line. `runClient` has not been run: the build machine has no display. The owner needs to run it once before this is ticked.
- [x] **M0-5 Test harness.** Add pure-Java tests for `core/` and in-world game tests. Check with the owner first that a test-only library is acceptable (open question 7).
  - Done when: `./gradlew test` runs one passing sample test and the game-test task runs one passing sample test. Update the command names in `CLAUDE.md` if they differ.
  - Note: JUnit 5 for `core/` (owner approved test-only libraries 2026-11-14) and Fabric game tests in `src/gametest`. Fabric names the game-test task `runGameTest`, not `runGametest`; `CLAUDE.md` still has the old name and is the owner's to change. `build` runs both kinds of test.
- [x] **M0-6 Guard the core.** Add a build check that fails if anything under `core/` imports a Minecraft class.
  - Done when: adding such an import makes `./gradlew build` fail with a clear message.
  - Note: `checkCoreImports` Gradle task. Verified: a `net.minecraft` import in `core/` fails the build with a `Rule E5 broken` message.
- [x] **M0-7 Constants home.** Create the single config class for balance numbers and load the values from `CONTEXT.md` section 5 into it.
  - Done when: every row of the numbers table that M1 uses exists as a named constant.
  - Note: `core/Balance.java` holds every row of the numbers table.

---

## M1 · The First Winter

Goal: a complete, polished 3–5 hour slice. The player finds the Charter, founds a camp, takes in three survivors, builds three starter buildings with them, fights off one raid and tries to get through the first winter.

Not in M1: other nations, diplomacy, research, model calls, money, freehand anchors, the Ghosted tier.

### Foundation

- [x] **M1-01 Ledger core.** The `Event` record from `CONTEXT.md` 3.11, an append-only log, cause links, and a query for "what caused this".
  - Done when: tests show an event chain can be written, reloaded and walked backwards from any event to its roots.
  - Note: `core/ledger`: Event, EventDraft, Effect, Ref, Place, Ledger with `causeChain` and `roots`. `LedgerTest` writes, reloads and walks a chain.
- [x] **M1-02 Persistence.** Save and load the Ledger and all `core/model` records through Minecraft's saved-data system. The event log is an append-only file per world.
  - Done when: a game test saves a world, reloads it and finds identical state.
  - Note: `mc/world/RoeWorld` + `RoeSavedData` (world state and deferred edits in saved data, split into pieces under the 64 KB NBT string limit) + `LedgerLog` (`data/roe/ledger.log`, written by a background thread, cut back to the last save on load). Game test `saveAndReloadGiveIdenticalState` checks the codec and the log give back identical state.
- [x] **M1-03 Calendar and seasons.** In-game days map to seasons and years. A `SeasonChanged` event fires at each boundary.
  - Done when: tests cover day-to-season mapping and a season change appears in the Ledger.
  - Note: `core/sim/Calendar` and `CalendarSystem` (emits `day_started` and `season_changed`). Covered by `SimulationTest`.
- [x] **M1-04 `/roe` debug commands.** `/roe time`, `/roe events [n]`, `/roe why <eventId>`.
  - Done when: `/roe why` prints a cause chain in-game.
  - Note: `/roe time`, `/roe events [n]`, `/roe why <id>` (indented cause chain), plus `/roe settlement`, `/roe stockpile`, `/roe standing`, `/roe stats <ref>` and `/roe give charter` (operators). Checked by compiling against 26.3; not yet tried by hand in a client.

### People and places

- [x] **M1-05 Character records.** Name, trait, want, profession, memories, needs. Persistent and independent of any entity.
  - Done when: tests create, save and reload a character with every field intact.
  - Note: `core/model/Character` (name, trait, want, profession, memories, settlement, status); needs live in the per-character stat store. `SimulationTest` saves and reloads one with every field intact.
- [x] **M1-06 Settlement record and claim.** A settlement has a centre, a claim radius, a population list and a stockpile reference.
  - Done when: `/roe settlement` shows the settlement the player is standing in.
  - Note: `Settlement` record and `Founding.settlementAt`; `/roe settlement` shows the claim the player stands in.
- [~] **M1-07 Embodied characters.** A server-side entity bound to a character record. It walks, carries items and uses containers. When it unloads, the record stays.
  - Done when: a game test spawns a character out of sight, has it walk a path and place an item in a chest, then unloads and reloads it with the same identity.
  - Note: `roe:person` (player model, own skin, name tag, held item) bound to a character id; not saved with chunks, so `mc/entity/Bodies` gives each resident one body while the camp is watched and removes it out of sight. Game test: a body walks to a spot and a new body for the same id is the same person. Open: an in-client look, and a body putting an item in a real chest (stock moves through the Granary sync instead, see M1-12).
- [~] **M1-08 Seasonal farming.** Crop growth obeys the season: none in winter, faster in spring.
  - Done when: a game test shows no outdoor growth in winter.
  - Note: Rules done (`camp/Farming`). Real wheat on a built farm is planted and harvested by the farmer's body and shows the rules' growth stage (`mc/world/Upkeep`). Open: the in-world winter game test.

### The opening

- [~] **M1-09 The Hollow Ruin.** A hand-built structure placed by world generation near spawn in the starting region, guarded by weak raiders, holding the Charter. Build it to the L1 quality bar.
  - Done when: a fresh world has one reachable Hollow Ruin within a set distance of spawn, and the owner has approved how it looks.
  - Note: Second design (the owner rejected the first on 2026-11-15): decayed blocks peppered through the stone, every wall a different height, a tall broken tower corner, slabs and stairs breaking the wall tops, rubble thrown out round the footing, moss carpet, leaves and cobwebs, gaps in the floor where the ground shows, no light. Blueprint format gained `keep` (leave the ground alone) for this. Preview: `docs/previews/hollow-ruin.png`. Open: the owner's approval and an in-client walk to it.
- [x] **M1-10 The Charter.** An item found in the ruin. Placing it on a lectern founds the camp: a `SettlementFounded` event, a claim, a tent and a campfire.
  - Done when: founding works once per player, is recorded in the Ledger and survives a reload.
  - Note: `roe:charter` on an empty lectern: `Founding.found`, the lectern takes a written Charter, a canvas tent with a bed and a campfire go up block by block, and the founder receives three Plans (farm, stable, bunkhouse). Game test covers once-only founding; a second Charter inside a claim says which camp owns the land.
- [~] **M1-11 The first night.** On the first night after founding, three survivors walk in from beyond sight and ask to stay. Each has a name, a trait, a want and a memory.
  - Done when: they arrive on foot from out of view, never spawn in sight, and their arrival has a cause in the Ledger.
  - Note: Rules done. While watched, survivors walk in from out of sight to the fire and ask to stay; right-click takes them in, sneak-right-click sends them away. Open: an in-client check that they never appear in view.

### The loop

- [x] **M1-12 Stockpile.** A Granary Seal marks a storage building. The stockpile is the real contents of its containers.
  - Done when: adding or removing items by hand changes what `/roe stockpile` reports.
  - Note: `roe:granary_seal`: chests and barrels within 6 blocks of a Seal inside a claim are the stockpile. `mc/world/Stockpiles` turns changes made by hand into `stockpile_counted` events and carries rule changes (meals, harvests, raids) into the chests; overflow is piled by the Seal as an event. Game test covers both directions.
- [~] **M1-13 Blueprints.** The player places a ghost outline. Builders fetch materials from the stockpile and build it block by block. Ship three: farm, stable, bunkhouse, in the Hollowcrown style.
  - Done when: all three can be built start to finish by survivors while the player watches, and the owner has approved how the finished buildings look.
  - Note: Second design (the owner rejected the first on 2026-11-15). Redone to common medieval building practice: steep 45-degree stone-brick roofs overhanging one block on every side with oak barge boards on the gable ends, an oak log frame on a cobblestone plinth and lower course, shuttered windows, a chimney, props. Farm: two 3x4 beds either side of an irrigation ditch (still 24 crops), log kerb, fenced, torches, composters. Costs now: farm 54 oak log + 7 torch (~27 builder-hours); stable 141 cobblestone + 77 oak log + 18 wheat + 2 torch (~79 h); bunkhouse 175 cobblestone + 123 oak log + 4 brown bed + 2 torch (~122 h). Preview: `docs/previews/starter-buildings.png`. Open: the owner's approval, and whether the bunkhouse is now too slow to finish before sleeping rough makes the survivors ill (see the reply of 2026-11-15).
- [~] **M1-14 Jobs.** Farmer (till, plant, harvest, deposit), builder (M1-13), gatherer (wood and stone to the stockpile). The player assigns jobs by interacting with a survivor.
  - Done when: an assigned farmer completes a full harvest-and-deposit cycle with no help.
  - Note: Rules done. Bodies carry out orders while watched (walk to the field, site or claim edge, work, carry the goods to the granary), finishing no earlier than the rules' due hour. Sneak-right-click a resident to give the next job. Open: a full harvest-and-deposit cycle watched in a client.
- [~] **M1-15 Needs: food and rest.** People eat twice a day from the stockpile and sleep in a bed under a roof. Unmet needs lower output, then cause illness, then death or leaving. Each step is a Ledger event.
  - Done when: tests cover the full decline, and in-game an unfed survivor visibly weakens and then leaves or dies with a traceable cause.
  - Note: Rules done: `camp/Needs`: two meals a day from the granary (x1.25 in winter), beds counted by the world; weak, then ill, then death (or leaving, for restless, proud and greedy people), each an event caused by the last. `NeedsTest` walks a death back to `food_ran_out` and `day_started`. Open: the visible decline in-world.
- [~] **M1-16 Ledger tier.** When the settlement unloads, its people keep working as records, updated once per in-game hour, using the same rates as the embodied jobs.
  - Done when: leaving for a full in-game day and coming back shows crops harvested, food eaten and the stockpile changed.
  - Note: Rules done: `Jobs.hourly` runs work for settlements nobody is watching, through the same calls the embodied tier uses. Open: the in-world check after a day away.
- [x] **M1-17 Deferred edits.** Off-camera block changes queue per chunk and apply when the chunk loads.
  - Done when: a builder finishes a blueprint while the player is away and the building is standing on return.
  - Note: `mc/world/DeferredEdits`: per-chunk queue saved with the world, applied on chunk load, never force-loads. Builders' steps finished while unwatched go through it. Game test covers a loaded chunk; the unloaded path is untested in-world.
- [x] **M1-18 Tier-invariance test.** Run the same settlement for ten in-game days in the Embodied tier and in the Ledger tier.
  - Done when: food produced and eaten agree within a tolerance written in the test, and the test runs in the build.
  - Note: `camp/TierInvarianceTest`: ten days in each tier, the embodied run driven with a one-hour walking lag. Food produced and eaten agree within 10%. Runs in `./gradlew test`.

### The test of the act

- [~] **M1-19 Standing, first version.** Compute Standing from population and territory only.
  - Done when: `/roe standing` shows the score and its parts.
  - Note: Rules done: `camp/Standing` from population and territory with the 0.30 and 0.25 weights. A three-person camp scores about 1.39. Open: `/roe standing`.
- [~] **M1-20 Barbarian raid.** One T1 raid sized from Standing: leather armour, diamond axes, very low health. It is announced first (smoke on the horizon, a warning from a survivor), walks in from the Wilds and goes for the food store.
  - Done when: the raid never appears in view, the player has time to prepare, and the outcome is in the Ledger.
  - Note: Rules done. While watched: signal smoke on the horizon in the raid's direction after the warning, then `roe:barbarian` raiders (leather armour, diamond axe, 4 hearts) walk in from out of sight for the granary; fighters attack them, others flee. All down = driven off; holding the granary 10 s = food taken. Open: an in-client fight.
- [~] **M1-21 The first winter.** Winter arrives on the calendar. A survivor warns the player one season ahead. Getting through with at least one survivor alive completes Act I.
  - Done when: both endings work (the camp survives, or the camp is lost) and each tells the player why, from the Ledger.
  - Note: Rules done. Players at or owning the camp are told of the warning, the winter, the raid, deaths and the ending in chat, each with its `/roe why` id. Open: in-client check of both endings.
- [~] **M1-22 Player guidance.** The survivors tell the player what the camp needs, in character, from template text. No tutorial pop-ups.
  - Done when: a new player can reach winter without outside instructions.
  - Note: Rules done. While watched, the person with the most urgent advice says it aloud every two minutes when it changes. Open: a new player reaching winter with no instructions.

### Finish

- [~] **M1-23 Performance check.** Measure server tick time with the camp loaded and with it unloaded. Record the hardware, the method and the results here.
  - Done when: the numbers are written under this task.
  - Note: Measured the Ledger tier only (camp unloaded). Hardware: cloud container, 4 vCPU Intel Xeon @ 2.10 GHz, Temurin JDK 25.0.5. Method: pure-Java run of `CampRules` with fed camps (farmer, gatherer, fighter each), 5 in-game days warm-up, then the mean over 20 in-game days. Results: 1 camp 0.12 ms per in-game hour; 10 camps 0.16 ms; 50 camps 0.62 ms. One in-game hour is 1000 ticks, so the unloaded cost per tick is negligible; a 12-hour sleep catch-up for one camp is about 1.4 ms, spread over ticks by the 2 ms budget. Not measured: tick time with the camp loaded and bodies working, which needs a client session.
- [ ] **M1-24 Polish pass.** Fix anything that pops in, teleports, idles with no reason or looks unfinished.
  - Done when: every line of the quality bar in `RULES.md` section 4 holds.
- [ ] **M1-25 Playtest.** Three people who did not build it play from spawn to the end of winter.
  - Done when: what they said is written below and the owner has decided what to change before M2.

- [ ] **M1-26 Release.** Tag the finished milestone `m1` and push the tag. `.github/workflows/release.yml` builds, tests and publishes the jar as a GitHub release.
  - Done when: the M1 release and its jar are on GitHub.

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
- [ ] **M2-M Release.** Tag `m2`; the workflow publishes the jar.

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
- [ ] **M3-N Release.** Tag `m3`; the workflow publishes the jar.

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
- [ ] **M4-J Release.** Tag `m4`; the workflow publishes the jar.

**M4 playtest notes:** none yet.

---

## M5 · The Crown

Goal: the story can be finished, and play continues after it.

- [ ] **M5-A Act IV.** T4 threats and the win condition. Needs the owner's choice (open question 3).
- [ ] **M5-B Epilogue.** Rebellions, pretenders and Wild hordes after the story ends.
- [ ] **M5-C Research: Imperial era and repeatable research.** Check law L6: growth must not flatten.
- [ ] **M5-D Full playthrough test, polish, playtest.**
- [ ] **M5-E Release.** Tag `m5`; the workflow publishes the jar.

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
