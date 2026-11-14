# Rise of Empires

A Fabric mod that turns Minecraft into a grand-strategy game. The player climbs out of a collapsed realm and becomes ruler of four. Every settlement, army and rival nation runs on one shared simulation, and every outcome has a traceable cause.

**Current state: nothing is built.** The design is a proposal. No number in it has been tested.

## Read these first

@CONTEXT.md
@RULES.md

Then open `TASKS.md` and find the first unchecked task. That file is the only source of what to work on next.

| File | What it holds | Who changes it |
|---|---|---|
| `CONTEXT.md` | The design: world, systems, numbers, decision log | Owner decides. Claude edits only to record a decision the owner made. |
| `RULES.md` | Design laws and engineering rules | Owner only |
| `TASKS.md` | Ordered work, one milestone at a time | Claude ticks tasks and adds notes |
| `CLAUDE.md` | This file | Owner only |

## How to work in this repo

1. Take the first unchecked task in `TASKS.md`. Do not skip ahead, and do not start a later milestone.
2. Before writing code for a task that adds a new system, write a short plan with a diagram (Mermaid is fine) and show it to the owner. Structure comes before code.
3. Build the smallest thing that meets the task's "Done when" line. Add nothing the task does not ask for.
4. Run the checks in the next section. A task is not done until they pass.
5. Tick the task in `TASKS.md` and add one line under it saying what was built and anything the owner should know.
6. If a task needs a decision marked `OPEN` in `CONTEXT.md`, stop and ask the owner. Do not pick an answer.
7. If a `PROPOSED` decision turns out to be wrong or unbuildable, say so plainly, with the evidence, and wait for the owner. Do not quietly change it.

## Commands

Versions are pinned in `gradle.properties` during task M0-1. Until then these commands do not exist.

```bash
./gradlew build
```
Expected: `BUILD SUCCESSFUL`, and a mod jar in `build/libs/`.

```bash
./gradlew test
```
Expected: `BUILD SUCCESSFUL`. Runs the pure-Java simulation tests. These must stay fast (under a minute in total).

```bash
./gradlew runClient
```
Expected: a Minecraft client opens with the mod loaded. The log contains `[riseofempires] initialised`.

```bash
./gradlew runServer
```
Expected: a dedicated server starts with no client classes loaded. Use this to catch client-only code in server paths.

```bash
./gradlew runGametest
```
Expected: all in-world tests pass and the process exits with code 0. (Added in task M0-5. If the pinned Fabric version names this task differently, update this line.)

## Layout

```
src/main/java/<group>/riseofempires/
  core/        Pure Java simulation. No Minecraft imports. All rules live here.
    ledger/    Events, causes, the append-only log
    sim/       Calendar, needs, production, migration, standing
    model/     Character, Settlement, Nation, Stockpile records
  mc/          Minecraft adapters. Reads and writes the world for core.
    entity/    Embodied characters and navigation
    block/     Anchor blocks, the Charter
    world/     Structures, deferred edits, saved data
    command/   /roe debug commands
  ai/          The Adjudicator client. Async only. (Milestone 3)
src/main/resources/data/riseofempires/
               Blueprints, research, calamities, realm data as data packs
src/test/java/ Pure-Java tests for core/
src/gametest/  In-world tests
```

`<group>` is a placeholder. The owner picks it in task M0-1.

## Definition of done, for every task

- `./gradlew build` and `./gradlew test` pass.
- New rules in `core/` have a test.
- Every state change goes through a Ledger event with its causes filled in.
- Nothing appears or changes in the player's view without a cause the player could discover.
- No new dependency was added.
- `TASKS.md` is updated.
