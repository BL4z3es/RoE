# Context

The design of Rise of Empires as it stands. Tags follow `RULES.md` section 2: `OWNER` is decided by the owner, `PROPOSED` is Claude's choice and open to change, `OPEN` is undecided.

**Status: design proposal. Nothing is built. No number below has been tested or benchmarked.**

---

## 1. What this is

A Fabric mod for Minecraft Java Edition. `OWNER`

- It is still Minecraft. Building, mining and survival stay as they are. `OWNER`
- What changes is progression. The vanilla path of better ores, other dimensions and bosses is replaced by finding nations, then conquering them, trading with them, or outgrowing them. `OWNER`
- The world is the interface. The player walks through cities and sees what happened, in place of reading numbers on a screen. `OWNER`
- The player acts as the ruler. Workers do the physical work, and the player can watch them do it: a farmer harvests, carries the crop and puts it in storage. `OWNER`
- Rules decide outcomes. A language model fills story gaps and judges open-ended actions. `OWNER`
- Inspirations: Hearts of Iron, and two other titles the owner named by voice that were not clearly captured. `OPEN`

### Modes

| Mode | Status | Summary |
|---|---|---|
| Story | `OWNER` core | Four realms. The player's realm is in chaos. Rise to rule all four. |
| Open World | `OWNER` proposed for later | Infinite world, no end goal. |
| Server | `OWNER` proposed for later | Players against players. The host runs the models. |

In single player, the person running the game supplies the model: an API key or a local model. `OWNER`

---

## 2. Story mode

### Premise `OWNER`
The world is divided into four realms, each with its own strengths and weaknesses. The realm the player spawns in is in chaos, and the others are trying to take it. The player survives, rises, and becomes supreme ruler of all four.

### Setting: the Sundered Crown `PROPOSED`
One empire once held all four realms. The last emperor died with no heir and four regents split the land. Three founded stable realms. The heartland, which holds the Crown Seat, fell into civil war. Whoever holds the Crown Seat has a claim to all four realms, so the other three keep invading.

All names below are placeholders. `PROPOSED`

| Realm | Position | Land | Strength | Weakness | Building style |
|---|---|---|---|---|---|
| Hollowcrown (start) | Centre | Plains, forest, rivers | Ruins hold lost knowledge, so salvage research is fast | No order, raided from three sides | Oak, cobblestone, stone brick |
| Frostmere | North | Taiga, snow, mountains | Iron, disciplined soldiers | Short growing season, imports food | Spruce, deepslate |
| Sunreach | South | Desert, savanna, badlands | Gold, trade, mercenaries | Water, drought | Sandstone, terracotta |
| Thornwild | East | Jungle, swamp, dark forest | Food, fast population growth | Disease, little metal | Jungle wood, mud brick |

- Main antagonist: the Iron Regent of Frostmere. `PROPOSED`
- The Known World is 12,000 × 12,000 blocks centred on the heartland. Beyond it are the Wilds: normal generation plus barbarian tribes. `PROPOSED`
- The Nether opens as an industrial front in Act IV. The End stays vanilla. `PROPOSED`

### Opening `OWNER`
1. The player spawns and plays ordinary early Minecraft: wood, stone, iron.
2. They must take a special item from a structure (working name: Abandoned Ruins, similar to vanilla Trail Ruins).
3. That item lets them craft or place their first camp. After that the real game begins.
4. They put up a tent or house and live through the first night.
5. A few survivors arrive. This is the starting population.
6. Starter kits are available: a farm, a stable, a sleeping area for workers. These three buildings are the starting point of the empire.
7. Over time the player depends more on workers and does less by hand.

`PROPOSED` details: the structure is the Hollow Ruin. The item is the Charter, placed on a lectern to found the camp. Three survivors arrive, each with a name, a trait, a want and a memory. The first winter is the test of Act I.

### Acts and difficulty

Difficulty scales with progress. Stronger opponents appear as the player grows, and weaker ones stay visible. `OWNER`

Owner's examples: a new camp faces barbarians in leather armour with diamond axes and very low health. An empire with five vassal states faces a 100-strong army in full Netherite. `OWNER`

Obstacles are not only enemies: drought, a year-long winter and similar. Which ones is to be decided. `OWNER` / `OPEN`

| Act | Player becomes | Goal | Threat tier | Calamity |
|---|---|---|---|---|
| I · Ashes | Founder → Camp | Survive the first winter | T1: scales gear. 4–10 raiders. | First winter |
| II · Roots | Hamlet → Town | Take one heartland district | T2: scales organisation. 15–40 with a captain. | Drought or blight |
| III · The Seat | City → Heartland ruler | Take and hold the Crown Seat | T3: scales numbers and siege. 100–300. | Plague or the Long Winter |
| IV · Four Realms | Sovereign → Supreme ruler | Bring all three realms under the crown | T4: numbers and elite gear. 500–1,000+. | Realm-wide famine |
| Epilogue | Emperor | None. Play continues. | Rebellions, pretenders, Wild hordes | Any |

The act structure, tier axes and sizes are `PROPOSED`.

**Standing** `PROPOSED`: one score that threats scale to. It is driven by what the player has built, not by time played.

```
Standing = 0.30·log2(population) + 0.25·log2(territory in chunks)
         + 0.20·army strength + 0.15·research tier + 0.10·renown

Raid budget = base(tier) × Standing × (1 + 0.25 × hostility of the source nation)
```

A raid is bought from the attacking nation's real army and stockpile. Every raid is announced before it arrives. `PROPOSED`

---

## 3. Systems

All of section 3 is `PROPOSED` unless tagged otherwise.

### 3.1 The Age Before
World creation simulates about 200 years of history and places the results as real structures: towns, ruins, roads, battlefields. The world is finished before the player arrives (law L1). The Chronicle starts already filled in.

### 3.2 Settlements and building
- Cities are built from pre-made block choices, scripts or schematics, so that players who are not skilled builders still get a satisfying city. `OWNER`
- Two ways to build. **Blueprints:** a ghost outline that builders construct block by block from the stockpile. **Freehand:** the player builds anything and places an anchor block inside. The game checks the room and assigns a role.
- A validated freehand building can be saved as a blueprint and reused.
- Anchors: Hearthstone (housing), Granary Seal (food storage), Guild Anvil (smithy), Lectern of Office (town hall), Scholar's Desk (research), Altar Stone (chapel), Muster Post (barracks), Trade Ledger (market).
- Building quality comes from space per resident, material tier, light and decoration. It feeds comfort, Appeal and output.
- Settlement rank: Camp, Hamlet, Village, Town, City, Seat. Rank depends on population and anchors present.
- From the owner's sketch, settlement types to cover: capital, cities, bases, outposts. `OWNER`

### 3.3 People
- Workers are real characters in the world. `OWNER`
- Professions from the owner's sketch: farmers, builders, miners, fighters, scientists, church people, explorers, gatherers. `OWNER` Added: smiths, merchants. `PROPOSED`
- Every person is a persistent record: name, family, traits, profession, memories, a want. The record exists whether or not the body is loaded.
- Needs: food, rest, shelter, safety. Clergy supply faith, which reduces unrest during calamities.
- **Migration pool:** the Known World has a finite population. Each settlement has an Appeal score (food, housing, wages, safety, reputation, policy). Each season, people move from low Appeal to high Appeal within travel range. Growth takes people from somewhere else. This is the mechanism behind the owner's example of a policy angering a neighbour.

### 3.4 Economy
- Resource groups from the owner's sketch: ores, equipment, building materials, trades. `OWNER`
- Goods are real Minecraft items in real containers inside storage anchors.
- Nothing teleports. Carts and caravans carry goods along roads. A watched caravan is a real entity. An unwatched one travels on the Ledger with the same timing and risk.
- Currency: Marks, minted from gold at a Mint. Barter always works.

### 3.5 Calendar, seasons, calamities
- One season is 6 Minecraft days. One year is 24 days.
- Winter: no outdoor crop growth, food use +25%. Spring: growth ×1.25.
- Calamities roll per realm and are announced a season ahead by omens. Candidates: drought, blight, plague, the Long Winter, flood, failed harvest, mine collapse.

### 3.6 Research
- Technology is on the owner's sketch. `OWNER`
- Scholars produce Knowledge. Salvage from old-empire ruins gives bursts.
- Five eras: Ash, Iron, Guild, Crown, Imperial. Then repeatable research with no cap (law L6).
- Each era ends in a breakthrough that resets a cost curve, such as cheaper stone building or one governor running three towns.

### 3.7 Rule and consequence
- No action is blocked (law L4).
- Decrees are written in a Book and Quill and placed on the Lectern of Office. Common ones parse from templates. Others go to the Adjudicator.
- Four values carry consequences: **Loyalty**, **Fear**, **Memory** (specific acts, remembered by specific people) and **Renown** (rumour that travels along roads, with delay and distortion).

### 3.8 Other nations
- From the owner's sketch: opponents, vassal states, intelligence, raids, war. `OWNER`
- AI nations run on the same rules as the player (rule E1).
- Each ruler is a character with traits, goals and memory.
- Relations per pair of nations: opinion, trust, and grievances tied to the events that caused them.
- Agreements: trade pact, non-aggression, border treaty, alliance, vassalage, tribute, marriage.
- Letters are written in a Book and Quill and carried by a courier who can be intercepted.

### 3.9 War
- Armies are on the owner's sketch. `OWNER`
- Fighters are people. Recruiting a farmer removes a farmer.
- Orders go to formations through a War Banner item.
- **Rolling front:** about 120 fighters per side are embodied. The rest wait in the Ledger as named reserves and enter as others fall or rout.
- Unwatched battles are resolved by a deterministic calculator from each fighter's gear, skill, morale and terrain.
- Very large battles (the owner's earlier target was 1,000 against 1,000) are a goal. No size is promised until benchmarked. `OPEN`

### 3.10 Simulation tiers

| Tier | When | Runs as |
|---|---|---|
| Embodied | Within 48 blocks of a player | Real entities, full pathfinding and animation |
| Ghosted | Chunk loaded, farther than 48 blocks | Light entities on precomputed routes |
| Ledger | Chunk unloaded | Records updated once per in-game hour |

Results must match across tiers (rule E3). Off-camera world changes are queued per chunk and applied on load (rule E6).

### 3.11 The Ledger

```
Event {
  id, time, place,
  type        // harvest, death, decree, raid, migration ...
  actors[]    // characters, settlements, nations
  causes[]    // ids of the events that led here
  effects[]   // the state changes it applied
  witnesses[] // who saw it: drives rumour and memory
}
```

Systems talk only through events and shared pools: people, food, goods, Marks, renown. The in-game Chronicle is a view of this log.

### 3.12 The Adjudicator (GenAI)

Called only for open-ended outcomes: custom decrees, letters, unscripted actions, character reactions, story text.

```
Request:  the action text + facts the relevant characters know
          + a short list of allowed effect types

Response (JSON):
{
  "interpretation": "...",
  "effects": [ { "target": "...", "stat": "loyalty", "delta": -8, "duration_days": 24 } ],
  "narrative": "..."
}
```

Effects are clamped per category and applied as Ledger events. With no model, templates and rules-only judgement run in its place. Budget: about one call per player action and a few background calls per in-game day. The cost per session has not been calculated. `OPEN`

---

## 4. Technical foundation `PROPOSED`

- **Loader:** Fabric. The owner plays on Fabric.
- **Version:** pin the latest stable Minecraft release at task M0-1. For reference, Fabric's announcement for Minecraft 26.2 (June 2026) lists Loom 1.17, Loader 0.19.3 and Gradle 9.5.1, with the Loom plugin id `net.fabricmc.fabric-loom` and the game's own unobfuscated names. Fabric's porting notes point to Java 25 support. Check the Fabric develop page for current numbers before pinning.
- **Navigation:** a server-side pathfinder built on Minecraft's own navigation classes. Baritone is client-side and is not used.
- **Storage:** Minecraft's saved-data system plus an append-only event log per world.
- **Model calls:** the JDK's HTTP client to any OpenAI-compatible endpoint. Async only.
- **Mod id:** `riseofempires`. Java group: placeholder until M0-1. `OPEN`

---

## 5. Numbers

Starting values. All `PROPOSED`, all untested. Each must exist as a named constant (rule E25).

| Constant | Value | Used by |
|---|---|---|
| Season length | 6 in-game days | Calendar |
| Year length | 24 in-game days | Calendar |
| Winter food multiplier | 1.25 | Needs |
| Spring growth multiplier | 1.25 | Farming |
| Meals per person per day | 2 | Needs |
| Starting claim radius | 32 blocks | Founding |
| Starting survivors | 3 | Opening |
| Embodied radius | 48 blocks | Tiers |
| Ledger update interval | 1 in-game hour | Tiers |
| Embodied fighters per side | 120 | War |
| Known World size | 12,000 × 12,000 blocks | World generation |
| History length | 200 simulated years | The Age Before |
| T1 raid size | 4–10 | Threats |
| T2 raid size | 15–40 | Threats |
| T3 army size | 100–300 | Threats |
| T4 army size | 500–1,000+ | Threats |

---

## 6. Open questions for the owner

1. Final names for realms, rulers and the antagonist.
2. Which calamity anchors each act.
3. What counts as winning Act IV: conquest only, or any mix of conquest, vassalage and union.
4. Goals for Open World and Server modes.
5. Who pays for model calls in Server mode.
6. The Java group name and whether the mod id stays `riseofempires`.
7. Whether test-only libraries are acceptable under the no-dependencies preference.
8. The largest battle size to commit to, after benchmarks exist.
9. The two inspiration titles that were not captured clearly.

---

## 7. Decision log

Add a line whenever the owner decides something. Newest last.

| Date | Decision | By |
|---|---|---|
| 2026-11-12 | The game stays Minecraft. Progression changes to finding, conquering, trading with or outgrowing nations. | Owner |
| 2026-11-12 | Cities are physical and walkable. The world is the interface. | Owner |
| 2026-11-12 | The model fills story gaps and judges open-ended actions. Rules decide the rest. | Owner |
| 2026-11-12 | Opening: early survival, a special item from a ruin, first camp, first night, survivors, three starter kits. | Owner |
| 2026-11-12 | Buildings use pre-made kits or schematics so non-builders can keep up. | Owner |
| 2026-11-12 | The player is the ruler. Workers do visible physical work. | Owner |
| 2026-11-12 | Conquest, trade and peaceful growth are all valid ways to play. | Owner |
| 2026-11-13 | Story mode is the focus. Open World and Server modes are proposed for later. | Owner |
| 2026-11-13 | Story premise: four realms, the player's realm in chaos, rise to supreme ruler. | Owner |
| 2026-11-13 | Difficulty scales with progress. Weaker threats stay visible. | Owner |
| 2026-11-13 | Six hard rules adopted (see `RULES.md` section 1). | Owner |
| 2026-11-13 | Full system proposal written by Claude at the owner's request. Everything tagged `PROPOSED` awaits review. | Claude |
