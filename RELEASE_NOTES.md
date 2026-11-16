# Rise of Empires 0.1.1-prealpha (test build)

A test build of the first milestone, **M1 · The First Winter**.

**0.1.0-prealpha is broken: do not use it.** It froze every world, single-player or server, a
moment after a player joined. The Hollow Ruin was being built from inside Minecraft's
"chunk loaded" callback, which makes the server wait on itself for ever. No test had a player in
a normal generated world, so none saw it. 0.1.1 fixes that and adds the missing test.

This build has passed the unit tests, the in-world server tests, and a scripted playthrough of
the opening in a real client with the jar from this release: join a normal world, find the
ruin, found a camp, take in three survivors, live three days. **No person has played it yet.**
Expect bugs, and please write down what you see.

## Install

1. Install Minecraft **1.21.11** with **Fabric Loader 0.20.1** or newer.
2. Put **Fabric API 0.141.6+1.21.11** (or newer for 1.21.11) in your `mods` folder.
3. Put `riseofempires-0.1.1-prealpha+mc1.21.11.jar` from this release in your `mods` folder.
4. Start a **new** world. Java 21 is needed (the Minecraft launcher provides it).

## What to try

1. Play normal early Minecraft. The **Hollow Ruin** stands about 160 blocks from spawn: a broken
   stone ruin guarded by three weak raiders. Its chest holds the **Charter**; a **lectern** stands
   inside. (Shortcut for testing: `/roe give charter`, needs cheats.)
2. Put the lectern down and use the Charter on it. A tent and campfire go up beside it, and you
   receive three **Plans**: farm, stable, bunkhouse.
3. That first night, three survivors walk in and ask to stay. **Right-click** to take them in,
   **sneak + right-click** to send them away.
4. **Sneak + right-click** a resident to cycle their job: farmer, gatherer, builder, fighter, none.
   Right-click shows who they are and what they are doing.
5. Use a Plan on the ground inside your claim to stake out that building. Builders put it up
   block by block from the granary. Place a **Granary Seal** (get one with
   `/give @s roe:granary_seal`, needs cheats) with chests within 6 blocks: those chests are the camp's stores.
6. Keep people fed (two meals a day) and in beds under a roof, or they weaken, fall ill and die
   or leave. On day 8 or so a raid is announced (smoke on the horizon), arriving a day later.
   Winter comes on day 19. Reach spring with anyone alive and Act I is won.

## Commands

`/roe time`, `/roe events [n]`, `/roe why <eventId>` (why something happened), `/roe settlement`,
`/roe stockpile`, `/roe standing`, `/roe stats <ref>`.

## Known gaps

- Never played by a person. The scripted playthrough covers joining, the ruin, founding, survivors
  arriving and three days passing. It does not cover building a plan to the end, the raid fight or winter.
- The tent goes up wherever the lectern is, whatever the ground: on a slope or a shore it looks wrong.
- The Hollow Ruin is built a moment after its chunk loads, so on a slow machine it can be seen appearing
  in the distance.
- People can appear or vanish about 40 blocks from you, out of view but not over the horizon.
- A farm worked while you are far away shows no crops; only the granary changes.
- The Granary Seal has no crafting recipe and the mod's items are not in the creative menu yet.
- Balance is untested. The bunkhouse is slow to build; survivors without beds may fall ill first.
- Saves from this build may not load in later builds.
