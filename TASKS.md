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
- [x] **M0-2 Generate the project.** Create a Fabric mod project from the official template for the pinned version. Split client and common source sets.
  - Done when: `./gradlew build` prints `BUILD SUCCESSFUL` and a jar is in `build/libs/`.
  - Note: From `FabricMC/fabric-example-mod` branch `26.3`, with `splitEnvironmentSourceSets()`. Build passes and makes `build/libs/riseofempires-0.0.1.jar`. Dropped the template's example mixins, maven-publish, icon and CC0 licence. The mod has no licence: that is your call. You need JDK 25 to build.
- [x] **M0-3 Create the package layout** from `CLAUDE.md`: `core/`, `mc/`, `ai/`, with a `package-info.java` in each stating what belongs there.
  - Done when: the layout exists and `core/` compiles with no Minecraft imports.
  - Note: Three top-level packages under `io.github.bl4z3es.riseofempires`. Sub-packages (`core/ledger` and so on) get created by the tasks that first need them, not as empty folders now. The no-Minecraft rule is only written down until M0-6 enforces it.
- [x] **M0-4 Log on start.** The mod logs `[riseofempires] initialised` on server start.
  - Done when: `./gradlew runClient` and `./gradlew runServer` both show the line.
  - Note: Logged from Fabric's `SERVER_STARTED` event, so it fires on the dedicated server and on the client's integrated server once a world opens. Checked both on 2026-11-21. Fabric prints it as `[Server thread/INFO] (riseofempires) initialised`, in round brackets. `CLAUDE.md` expects square brackets; that file is yours to fix. Running the client headless needs Xvfb, `SDL_VIDEO_FORCE_EGL=1`, `onboardAccessibility:false` in `run/options.txt` and an existing world for `--quickPlaySingleplayer`. The owner accepted the Minecraft EULA for dev runs.
- [x] **M0-5 Test harness.** Add pure-Java tests for `core/` and in-world game tests. Check with the owner first that a test-only library is acceptable (open question 7).
  - Done when: `./gradlew test` runs one passing sample test and the game-test task runs one passing sample test. Update the command names in `CLAUDE.md` if they differ.
  - Note: JUnit 5.14.4 runs `src/test`, test scope only. Loom's `fabricApi.configureTests` sets up `src/gametest` as a separate test mod run by `./gradlew runGameTest` (capital T, so `CLAUDE.md` is updated). Both sample tests pass. I broke the game test on purpose to check it really runs, and the run failed. `./gradlew build` also runs the game tests: a full build takes about 15 s now. The jar contains no test classes and no JUnit. The game-test runner reports 2 tests. One is ours. The other is not, and I have not found which mod registers it.
- [x] **M0-6 Guard the core.** Add a build check that fails if anything under `core/` imports a Minecraft class.
  - Done when: adding such an import makes `./gradlew build` fail with a clear message.
  - Note: `checkCoreIsPure` runs before both compile steps. It scans `core/` in `src/main` and `src/test` for `net.minecraft`, `com.mojang` or `net.fabricmc`, whether imported or written out in full, and skips comments. It fails with the file, the line and what to do. Checked with a test file: the build failed on lines 5 and 8, as expected, and passed again once the file was removed. I added Mojang and Fabric to the ban because they exist only because the game does. Reflection or a string-built class name would get past it; reviews have to catch those.
- [x] **M0-7 Constants home.** Create the single config class for balance numbers and load the values from `CONTEXT.md` section 5 into it.
  - Done when: every row of the numbers table that M1 uses exists as a named constant.
  - Note: `core/Balance.java` holds the 10 rows M1 uses, 11 constants in all (T1 raid size is a min and a max). Embodied fighters, world size, history length and T2–T4 are left out until a task needs them. One gap for you: `CONTEXT.md` 3.5 says winter has "no outdoor crop growth", but that number is not in the section 5 table. M1-08 needs it. Add a row such as "Winter growth multiplier: 0" and I will make it a constant. I did not add it myself because the table is yours.

---

## M1 · The First Winter

Goal: a complete, polished 3–5 hour slice. The player finds the Charter, founds a camp, takes in three survivors, builds three starter buildings with them, fights off one raid and tries to get through the first winter.

Not in M1: other nations, diplomacy, research, model calls, money, freehand anchors, the Ghosted tier.

### Foundation

- [x] **M1-01 Ledger core.** The `Event` record from `CONTEXT.md` 3.11, an append-only log, cause links, and a query for "what caused this".
  - Done when: tests show an event chain can be written, reloaded and walked backwards from any event to its roots.
  - Note: `core/ledger`: `Event`, `Ledger` (ids count up from 1; an event may only cite causes already logged, so chains never loop), `causeChain`, `roots`, `LedgerCodec`. `LedgerTest` writes a branching chain, reloads it and walks every event back to its roots.
- [x] **M1-02 Persistence.** Save and load the Ledger and all `core/model` records through Minecraft's saved-data system. The event log is an append-only file per world.
  - Done when: a game test saves a world, reloads it and finds identical state.
  - Note: Records go through saved data (`mc/world/RoeSavedData`, bytes from `core/model/StateCodec`); the log is `<world>/data/riseofempires/ledger.bin`, appended on each save. `PersistenceGameTest` saves, reads both back from disk and compares byte for byte. The log is appended before the records are saved, so a crash in between leaves the log a few events ahead of the records, never behind.
- [x] **M1-03 Calendar and seasons.** In-game days map to seasons and years. A `SeasonChanged` event fires at each boundary.
  - Done when: tests cover day-to-season mapping and a season change appears in the Ledger.
  - Note: Day 0 is the first day of spring, year 1 (my choice; the design does not say). Time is the overworld clock, so sleeping moves the calendar; skipped meals and nights are caught up. `SeasonsTest`.
- [x] **M1-04 `/roe` debug commands.** `/roe time`, `/roe events [n]`, `/roe why <eventId>`.
  - Done when: `/roe why` prints a cause chain in-game.
  - Note: Also `/roe settlement`, `/roe stockpile`, `/roe standing`, `/roe perf`. `CommandsGameTest` runs them in-world against a real camp and checks `/roe why` walks back to the founding. Seen on a dedicated server too.

### People and places

- [x] **M1-05 Character records.** Name, trait, want, profession, memories, needs. Persistent and independent of any entity.
  - Done when: tests create, save and reload a character with every field intact.
  - Note: `core/model/Character`, `CharacterPersistenceTest`.
- [x] **M1-06 Settlement record and claim.** A settlement has a centre, a claim radius, a population list and a stockpile reference.
  - Done when: `/roe settlement` shows the settlement the player is standing in.
  - Note: Checked in `CommandsGameTest` with a command source standing in the claim, not with a real player.
- [x] **M1-07 Embodied characters.** A server-side entity bound to a character record. It walks, carries items and uses containers. When it unloads, the record stays.
  - Done when: a game test spawns a character out of sight, has it walk a path and place an item in a chest, then unloads and reloads it with the same identity.
  - Note: `mc/entity/CharacterEntity`. Walking measured at 8.0 ticks per block (9.1 in one run). Bodies sleep in a free roofed bed at night (as villagers do) and report it; condition slows them; the dead die in view, those who leave walk out of sight before vanishing. Skins are vanilla player skins, picked per character.
- [x] **M1-08 Seasonal farming.** Crop growth obeys the season: none in winter, faster in spring.
  - Done when: a game test shows no outdoor growth in winter.
  - Note: A mixin on `CropBlock.randomTick` (wheat, carrots, potatoes, beetroot). Indoor crops grow all year. `SeasonGameTest`.

### The opening

- [!] **M1-09 The Hollow Ruin.** A hand-built structure placed by world generation near spawn in the starting region, guarded by weak raiders, holding the Charter. Build it to the L1 quality bar.
  - Done when: a fresh world has one reachable Hollow Ruin within a set distance of spawn, and the owner has approved how it looks.
  - Note: Built, waiting for your approval of the look. 96–192 blocks from spawn, moved off water; each part is placed as its chunk is first generated, so no client ever sees it appear. Two guards. The design (from `tools/gen_structures.py`): a ruined 11x11 watch-house, cobble and mossy base, half-timbered oak on two sides and cracked stone brick on two, a broken roof, rubble, cobwebs, vines, a dais with the chest, a lectern and a banner. A live server run found a deadlock (filling the chest during chunk generation); fixed. Not yet seen with a client.
  - Note (2026-11-23): Seen with a real client now (`ReviewScreenshotsClientGameTest`). The first fresh world put half the ruin in a river with a guard swimming: the "off water" check only tested the centre column. It now needs the whole footprint dry and within 3 blocks of level, and searches inside the 96–192 ring. Checked in 3 random-seed worlds per run. Pictures for your approval are in `build/run/clientGameTest/screenshots/` after `tools/client_gametest.sh`.
- [x] **M1-10 The Charter.** An item found in the ruin. Placing it on a lectern founds the camp: a `SettlementFounded` event, a claim, a tent and a campfire.
  - Done when: founding works once per player, is recorded in the Ledger and survives a reload.
  - Note: The tent and campfire go up block by block over a few seconds. The camp is named "<player>'s Camp" (my choice). `OpeningGameTest`.
- [x] **M1-11 The first night.** On the first night after founding, three survivors walk in from beyond sight and ask to stay. Each has a name, a trait, a want and a memory.
  - Done when: they arrive on foot from out of view, never spawn in sight, and their arrival has a cause in the Ledger.
  - Note: They set out at dusk, 96 blocks out, away from every player and out of their line of sight. `SurvivorArrivalGameTest` covers it at 24 blocks, plus a mock player standing where they would spawn. The six names, traits, wants and memories in `data/riseofempires/survivors/hollowcrown.json` are placeholders for you to replace.
  - Note (2026-11-23): A real client with a real player found that this did not work in play. (1) Survivors set out at 12,500, the same tick bodies start their night routine, and someone with no camp yet froze in place until the walk timed out and joined as a record with no body. (2) On open ground with the player in camp, every spot 96 blocks out is in sight, so no body was ever placed. Both are fixed: night is only for people who belong to a camp, and the spawn search moves out ring by ring past the 128-block sight limit. `CharacterGameTest` now covers (1) (fails before, passes after); `NothingPopsInClientGameTest` covers both with a real client. Known gap for you: at render distance 6 or less the world loads only ~90–100 blocks around the player, so survivors cannot start 96 blocks out; they fall back to joining as records. See Ideas.

### The loop

- [x] **M1-12 Stockpile.** A Granary Seal marks a storage building. The stockpile is the real contents of its containers.
  - Done when: adding or removing items by hand changes what `/roe stockpile` reports.
  - Note: Containers within 6 blocks across and 3 up or down of the seal. Changes made off-camera wait as "pending" and are written into the chests when the area loads. `StockpileGameTest`. Breaking the seal does not unseal the granary yet (fixed 2026-11-23, see M1-24).
- [!] **M1-13 Blueprints.** The player places a ghost outline. Builders fetch materials from the stockpile and build it block by block. Ship three: farm, stable, bunkhouse, in the Hollowcrown style.
  - Done when: all three can be built start to finish by survivors while the player watches, and the owner has approved how the finished buildings look.
  - Note: Built, waiting for your approval of the look. Two builders finished the farm in 5,851 ticks, the stable in 6,551 and the bunkhouse in 24,307 (`StarterBuildingsGameTest`, manual only). The "ghost" is particle edges plus a marker on the next block; a real translucent ghost needs client rendering (in Ideas). Farm: 9x9 fenced, water channel, 41 wheat plots. Stable: open-fronted, three stalls, two pens. Bunkhouse: 9x7 cabin, four beds.
  - Note (2026-11-23): Real-client pictures of all three finished buildings, and of the founder's tent, are made by `ReviewScreenshotsClientGameTest`. My honest read: the farm and stable look deliberate; the bunkhouse (a box with a flat stepped plank roof) and the tent (a white wool wedge) look like placeholders by the L1 bar. Your call.
- [x] **M1-14 Jobs.** Farmer (till, plant, harvest, deposit), builder (M1-13), gatherer (wood and stone to the stockpile). The player assigns jobs by interacting with a survivor.
  - Done when: an assigned farmer completes a full harvest-and-deposit cycle with no help.
  - Note: An empty hand on a survivor cycles none, farmer, builder, gatherer. `FarmerGameTest`, `BuilderGameTest`, `GathererGameTest`. Only one builder works a site at a time; nobody works at night.
- [~] **M1-15 Needs: food and rest.** People eat twice a day from the stockpile and sleep in a bed under a roof. Unmet needs lower output, then cause illness, then death or leaving. Each step is a Ledger event.
  - Done when: tests cover the full decline, and in-game an unfed survivor visibly weakens and then leaves or dies with a traceable cause.
  - Note: The rules and the full decline are tested (`NeedsRulesTest`): missed meals plus rough nights reach strained at 2, weak at 4, ill at 6, and gone at 8, with even odds of leaving or dying. Bodies slow down, stop working when ill, and die or walk away. Not yet watched in a real game.
- [x] **M1-16 Ledger tier.** When the settlement unloads, its people keep working as records, updated once per in-game hour, using the same rates as the embodied jobs.
  - Done when: leaving for a full in-game day and coming back shows crops harvested, food eaten and the stockpile changed.
  - Note: `core/sim/LedgerTier` runs farm, gather and build work each hour from the same timings the bodies use. Crop ages are read from the world when the camp unloads and written back when it loads (`mc/farm/FarmSync`), and the store changes reach the chests. The parts are tested; a whole day away and back has not been watched with a player. Off-camera gathering does not remove trees or stone from the world yet (Ideas).
  - Note (2026-11-23): Now checked with a real player and client (`AwayForADayClientGameTest`), which found a real bug: the farm's crop ages were read from the world only at the moment the camp stopped being entity-ticking, when its chunks are usually already unloading. Nothing was read, the Ledger tier worked from stale ages (2 harvests instead of 41), and on return those stale ages were written over the ripe crops: 39 ripe crops silently became young with no harvest and no event. `FarmSync` now keeps a quiet copy of the world's ages while the camp is loaded and commits it in one `farm_surveyed` event on leaving. The check fails on the old code and passes on the new. Last run: the player flies 2,000 blocks away, a day passes, they walk back. All 41 ripe plots were harvested off-camera (43 harvests with regrowth), 72 seeds reached the chests, the day's 2 meals for 3 people came out of the store (6 wheat, exactly 24 quarter-meals), and every Ledger work event has causes. One caveat: the day is advanced an in-game hour at a time with short real waits, because client game tests tick the server in step with a software-rendered client and a real 24,000-tick wait takes about 40 minutes here.
- [x] **M1-17 Deferred edits.** Off-camera block changes queue per chunk and apply when the chunk loads.
  - Done when: a builder finishes a blueprint while the player is away and the building is standing on return.
  - Note: `mc/world/DeferredEdits`; edits wait until the chunk is loaded and no player is within 48 blocks. `DeferredEditsGameTest`.
- [x] **M1-18 Tier-invariance test.** Run the same settlement for ten in-game days in the Embodied tier and in the Ledger tier.
  - Done when: food produced and eaten agree within a tolerance written in the test, and the test runs in the build.
  - Note: `TierInvarianceTest`, tolerance 10%: over 20 seeds the embodied model made 105.45 wheat and the Ledger tier 105.00. Food eaten uses the same code in both tiers. The "embodied" side is a tick-by-tick model of the in-world rules, not the real entities.

### The test of the act

- [x] **M1-19 Standing, first version.** Compute Standing from population and territory only.
  - Done when: `/roe standing` shows the score and its parts.
  - Note: 0.30·log2(people) + 0.25·log2(chunks). A new camp of 3 with radius 32 scores about 1.6.
- [~] **M1-20 Barbarian raid.** One T1 raid sized from Standing: leather armour, diamond axes, very low health. It is announced first (smoke on the horizon, a warning from a survivor), walks in from the Wilds and goes for the food store.
  - Done when: the raid never appears in view, the player has time to prepare, and the outcome is in the Ledger.
  - Note: Planned on the first autumn day at least 2 days after founding; size 3 × Standing, kept between 4 and 10. A survivor warns of smoke a day before arrival, and a campfire burns where the raiders gather, 112 blocks out. Raiders have 3 hearts, take half the food if they reach the store for 5 seconds, and their deaths are counted; unwatched raids resolve as a theft. `RaidGameTest`. Spawning out of view has not been tested with a real player.
  - Note (2026-11-23): Tested with a real player and client (`NothingPopsInClientGameTest`). It found that on open ground with the player in camp no raider ever spawned (the hiding check had no distance limit), and the raid then hung in "arrived" forever, because a watched camp never takes the unwatched path. Fixed: past `Balance.SIGHT_LIMIT_BLOCKS` (128) nobody sees a body appear. Last run: raiders first reached the client 132–160 blocks out, too far to be drawn, and walked in. Left open: if no hidden spot can be found at all (e.g. very low render distance), the raid still waits; see Ideas.
- [~] **M1-21 The first winter.** Winter arrives on the calendar. A survivor warns the player one season ahead. Getting through with at least one survivor alive completes Act I.
  - Done when: both endings work (the camp survives, or the camp is lost) and each tells the player why, from the Ledger.
  - Note: Both endings and the warning are tested (`ActOneTest`). The ending message lists deaths, departures, thefts, missed meals and the warning, each with its event id. Not yet seen in a real game. The first winter starts on day 19 of the world, about 6 hours of play at 20-minute days, longer than the 3–5 hours the milestone targets. That is your call.
- [!] **M1-22 Player guidance.** The survivors tell the player what the camp needs, in character, from template text. No tutorial pop-ups.
  - Done when: a new player can reach winter without outside instructions.
  - Note: Built (`core/sim/Advice` and `mc/story/Storyteller`): a survivor speaks in chat about the next need in priority order (raid, granary, farm, farmer, builder, bunkhouse, food for winter, someone unwell). Whether a new player can reach winter needs the playtest.

### Finish

- [x] **M1-23 Performance check.** Measure server tick time with the camp loaded and with it unloaded. Record the hardware, the method and the results here.
  - Done when: the numbers are written under this task.
  - Note: Hardware: cloud container, 4 vCPU Intel Xeon @ 2.10 GHz, 15 GB RAM, Java 25 (Temurin), dedicated game-test server, no client. Method: `ROE_PERF=<case> ./gradlew runGameTest` (`PerfGameTest`), fresh world, 1,200 ticks measured after a 1,200-tick warm-up, two runs per case. Server tick mean (worst): no camp 0.55 / 0.86 ms (4.6 / 11.2); camp loaded with 3 bodies farming, building and gathering 1.29 / 0.86 ms (16.1 / 14.6); camp unloaded (Ledger tier) 0.48 / 0.77 ms (8.4 / 14.8). The Rise of Empires step runs once a second and averages 0.8–1.2 ms, capped by a 2 ms budget. Run-to-run noise is as large as the differences; the test world also holds about a dozen other test camps.
- [!] **M1-24 Polish pass.** Fix anything that pops in, teleports, idles with no reason or looks unfinished.
  - Done when: every line of the quality bar in `RULES.md` section 4 holds.
  - Note: Needs someone playing with a client. Known gaps: no textures of our own (the Charter, the seal and the blueprints reuse vanilla ones), the blueprint ghost is particles, and a builder idles while another builds the same site.
  - Note (2026-11-23): A real client can now be driven headless: `tools/client_gametest.sh` runs `./gradlew runClientGameTest` under Xvfb (needs `xvfb`, `libegl1`, `libgl1-mesa-dri`). It is not part of `./gradlew build`. `CLAUDE.md` does not list it yet; that file is yours. Done since: own 16x16 textures for the Charter, Granary Seal and three blueprints (`tools/gen_textures.py`, preview `docs/review/m1-textures.png`, awaiting your approval); breaking the Granary Seal now unseals the granary with a `granary_unsealed` event (pending off-camera changes are written to the chests first, or dropped with an effect line naming them); `GranaryUnsealGameTest`. Still open: builders sharing a site, which is also a rule E3 bug: the Ledger tier adds every builder's output while only one embodied builder may work a site, so two builders build about twice as fast off-camera. Flaky: `RaidGameTest.raidersTakeHalfTheFood` failed twice in about 15 runs (the raider never stole: 10 wheat, not 5). Not found yet; its failure message now reports the raider's position, order and time at the store, the raid's stage and the time of day, so the next failure explains itself.
- [!] **M1-25 Playtest.** Three people who did not build it play from spawn to the end of winter.
  - Done when: what they said is written below and the owner has decided what to change before M2.

**Numbers awaiting the owner.** Added while building M1 because a rule needed a number the design does not give. They live in `core/Balance.java` (bottom section) or as named constants marked `PROPOSED`; none is in the `CONTEXT.md` section 5 table yet: winter growth 0; meal times 1000 and 12000; night judged at 18000; a meal is 4 quarters; need thresholds 2/4/6/8; leave chance 0.5; missed meals forgiven 2 per meal; output 1.0/0.75/0.5/0; job timings (harvest 20, replant 10, chop 40, mine 30, place 20, deposit 20, take 20 ticks, walk 8 ticks a block, carry 16, gather radius 24); T1 raid base 3.0; raid timings (earliest day 2, sighting +6,000 ticks, arrival +24,000, origin 112 blocks, steal half, 100 ticks at the store); raider health 6; ruin 96–192 blocks from spawn with 2 guards; survivors start 96 blocks out at dusk (12,500); food values (`data/riseofempires/food_values.json`); granary reach 6 by 3; advice every 2,400 ticks. Added 2026-11-23: sight limit 128 blocks (`Balance.SIGHT_LIMIT_BLOCKS`, survivors and raiders); survivor spawn search ring step 8 blocks; ruin footprint may rise at most 3 blocks, sampled every 3 blocks, search turns 37 degrees, gives up on level ground after 120 spots.

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

- A true translucent blueprint ghost, drawn by the client (M1-13 uses particles).
- Off-camera gatherers should take real trees and stone out of the world through deferred edits (M1-16).
- The Ledger only grows. Large worlds will need old events condensed into summaries that keep their cause links.
- Several builders sharing one site (also a tier-invariance bug, see M1-24).
- At render distance 6 or less, the world around the player ends before the 96-block survivor origin and the 112-block raid origin. Survivors then join as records and a watched raid can wait forever. Options: start closer when nothing farther is loaded (weaker L1), or resolve the raid off-camera when no hidden spot exists (the theft happens unseen). Needs your decision.
- The real-client checks (`runClientGameTest`) could run in CI on a machine with Xvfb.
- A Granary Seal placed where the founder's tent is about to go up is replaced by the tent and unseals the granary (correctly, but the player did nothing wrong). The tent could avoid an existing seal, or the seal could refuse that spot.
- If the player teleports or respawns straight into camp, `FarmSync` defers the crop changes until they step 48 blocks away, so the fields show stale ripe crops meanwhile.
- Rule E27 asks for one branch and pull request per task; cloud sessions can push to one branch only, so M0 and M1 went in as separate commits on one branch.
