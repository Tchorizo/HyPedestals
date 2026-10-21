# HyPedestals

A Hytale server plugin that adds **Harvester** blocks: place one next to your crops and it harvests every ripe crop in range, storing the drops in its own inventory. Four material tiers (Copper, Iron, Thorium, Cobalt) — higher tiers cover a larger area, work faster and hold more items. Upgrades let a Harvester also water, fertilize and replant its farmland, and it can pass its harvest on to a chest next to it. The **Lumberjack** does the same for trees: it cuts whole trees around it, stores the wood and plants a sapling back.

**Keep animals on your farm and their manure becomes fertilizer.** With the Fertilizer Upgrade, every Poop your livestock drops inside the field is collected by the Harvester and used to fertilize the crops — no crafting, no bags, a closed loop from pasture to plant.

Requires **Hytale 0.7.2**. Works in singleplayer and on dedicated servers.

## Tiers

The area is a square centered on the Harvester (side `2R+1`) and 3 blocks tall (from one block below to one block above the Harvester).

| Tier | Block | Area | Interval | Slots | Recipe (Workbench) |
|---|---|---|---|---|---|
| 1 | Copper Harvester | 5×5×3 | 12 s | 9 | 12× Copper Bar + 10× any wood trunk |
| 2 | Iron Harvester | 7×7×3 | 8 s | 18 | 1× Copper Harvester + 15× Iron Bar |
| 3 | Thorium Harvester | 9×9×3 | 5 s | 27 | 1× Iron Harvester + 15× Thorium Bar |
| 4 | Cobalt Harvester | 11×11×3 | 3 s | 36 | 1× Thorium Harvester + 15× Cobalt Bar |

Upgrading is done by crafting: the previous tier is an ingredient of the next one. The contents of the old Harvester do not carry over, so empty it first.

The numbers in the table are the defaults. Radius and interval can be changed per tier in the [configuration](#configuration); the storage size lives in the block's asset JSON (`ItemContainerBlock` component). The `HyHarvester` component's `Radius`/`IntervalSeconds` in the asset are only used for a tier the configuration does not know (e.g. one added by another asset pack); there Radius must be 1–8 and IntervalSeconds > 0, and invalid values disable that block (one warning in the server log).

## Usage

1. Craft a Harvester at a Workbench.
2. Place it next to (or in the middle of) your crops.
3. Every interval it first collects dropped items around its area into its storage, then harvests **every ripe crop** in its area whose drops fit in its storage, then collects again. After a cycle that harvested something it keeps collecting for `PostHarvestCollectSeconds` (3 s by default), so the new drops reach its storage within the same cycle.
4. Press **Use** (F) on the Harvester to open its storage.

A Harvester only harvests a crop while its storage has room for its reservation (see [Known limitations](#known-limitations)). Before each crop it checks that the reservation still fits: at first the most that crop can drop (e.g. wheat and Life Essence: two new stacks, or one plus a stack of either with room), after a few harvests of that crop type 1.5 × what it was seen to drop (never more than the maximum). The check counts what this cycle already harvested and what the last two cycles harvested that has not reached the storage yet (the game takes a moment to make new drops collectable). The first crop that does not fit ends the cycle: it and the rest stay ripe on the plant until space frees up (e.g. through [export](#export-to-an-adjacent-container)), and the next cycle starts with it. While the storage is full the Harvester does not collect at all. Servers that want a limit on harvests per cycle can set `MaxHarvestsPerCycle`; the next cycle then continues where the last one stopped, so the whole area still gets harvested over a few cycles.

**Tip:** annual crops disappear when harvested, but the game's **Eternal** crops grow back after each harvest. Pair a Harvester with Eternal seeds for a farm that never needs replanting — or install the **Planter Upgrade** and keep seeds in the Harvester: it replants annual crops itself.

**Also harvests:** ripe apples on apple trees; the apples regrow after harvest. It leaves tree saplings, cacti and spreading mushrooms alone.

## Upgrades

A Harvester holds up to three upgrades, one of each kind. They are stored in the Harvester itself, not in its storage slots, so automation never takes them out.

> **Animals → manure → fertilizer.** Fence your cows, sheep, goats or horses inside the Harvester's area: their Poop is picked up like any drop and the Fertilizer Upgrade burns it (2 per soil block) before touching any Fertilizer bag. Manure above one stack is exported with the harvest, so the storage never clogs.

| Upgrade | Recipe (Farming Bench) | What it does |
|---|---|---|
| **Watering Upgrade** | tier 2: 1× Watering Can + 4× Iron Bar + 3× Tree Sap + 25× Life Essence | Keeps the tilled soil in the Harvester's area watered, like a watering can, with no water source needed and nothing consumed |
| **Fertilizer Upgrade** | tier 3: 1× Fertilizer + 4× Iron Bar + 25× Life Essence | Fertilizes the planted soil in its area with Poop and Fertilizer bags you put in the Harvester's storage (any slot) |
| **Planter Upgrade** | tier 2: 1× Iron Hoe + 4× Iron Bar + 25× Life Essence | Plants the seeds in the Harvester's storage (any slot) on every empty soil block of its area, and tree saplings on grass or dirt on a grid (never within 3 blocks of tilled soil) |

- **Install:** hold the upgrade and **right-click** the Harvester. One item is used (not in Creative). A Harvester that already has that upgrade tells you so and keeps your item.
- **Remove:** **crouch + Use (F)** on the Harvester takes out the most recently installed upgrade and puts it in your inventory (or drops it at your feet when the inventory is full). Plain Use still opens the storage.
- **Breaking** a Harvester drops its installed upgrades together with the block and its storage contents. Unloading the chunk or restarting the server keeps them installed.
- **Particles** show what is installed: glowing orbs over the Harvester, blue for watering, green for the fertilizer, amber for the planter, one ring per upgrade. They come from the block itself; with `UpgradeParticlesSeconds` above 0 (off by default; enable it if the block's own particles do not show on your client) the server also sends them every that many seconds. With `HarvestParticles` on (the default), a short puff of dust rises from the Harvester whenever a cycle harvests something.

Watering and Fertilizer work on a soil pass of their own, every `SoilSweepSeconds` (30 s by default), separate from the harvest interval and also while the storage is full. The pass covers the same area as the harvest; soil one block below the Harvester's level (under crops on the Harvester's own level) is included.

**Watering.** Each pass waters every tilled soil block in the area that has less than half of its watering time left, for the tier's `WateringSeconds` of game time: about 12 / 24 / 40 / 60 real minutes for Copper / Iron / Thorium / Cobalt. While the Harvester runs the soil never dries; after it is removed (or its chunk unloads) the soil stays watered for that long.

**Fertilizer.** With the upgrade installed, the Harvester fertilizes with **Poop and Fertilizer bags from any slot of its storage**; no slot is reserved and nothing is filtered, so the harvest can use every slot. Each pass fertilizes the soil blocks in the area that have a crop on them and are not fertilized yet, one application per block. Poop goes first: **animal manure in the Harvester's storage fuels it: K poop per application** (`ManurePerApplication`, 2 by default, the same poop per use as a crafted bag; poop from several slots adds up, the lowest slot is used first, and a rest below K waits for more). The Harvester collects every item dropped in its collect box, so **keep livestock inside the fenced field**: cows, sheep, goats, horses, pigs and other farm animals drop poop every 12–72 real minutes and the Harvester picks it up. Then the bags: one bag use per block (5 uses per bag, like using the bag by hand); an empty bag disappears. With several bags, the most used one goes first, so bags are emptied one at a time. While the upgrade is installed the bags are never exported and the poop is kept up to `ManureStock` (100 by default, one stack); the poop above that is exported (with `ManurePerApplication` 0 poop is an ordinary item again). Fertilized soil stays fertilized until the soil block is replaced. Anything that inserts items into containers can also feed poop or bags to the Harvester.

**Planter.** With the upgrade installed, right after every harvest cycle the Harvester plants from its storage, any slot, **lowest slot first**:

- **Seeds** go on every empty block of its area whose block below the seed grows on — the same rule as planting by hand: vanilla seeds on tilled soil (watered or fertilized too), herb seeds on crystal soil, and seeds of mods that follow the game's seed format on their own soils (e.g. LifeCrops seeds on its essence soil). One seed per block; an annual crop harvested in a cycle is replanted in the same cycle. Tree seeds are not planted.
- **Saplings** (`Plant_Sapling_*`) go on grass, dirt or another non-tilled soil block, only on a grid every `SaplingSpacing` blocks (4 by default) from the Harvester along both axes, only where the sapling and the blocks around and above it are free, and **never within 3 blocks of tilled soil** (a grown tree's roots would eat into the field). With the default spacing only Thorium and Cobalt Harvesters have grid spots (up to 8 on the edges of the area, fewer next to a tilled field); set `SaplingSpacing` to 3 for Iron. A growing tree never replaces a chest, crop or Harvester in its way, but it stops growing where it is blocked, so leave room around saplings. Apples on apple trees are harvested like any crop: apple saplings make an orchard.
- The export keeps up to `PlanterSeedStock` (64 by default) of **each** seed and sapling kind in the Harvester; the rest leaves with the harvest.
- Empty tilled soil turns back into dry mud after about an in-game day and a half (about an hour), the game's own rule; keep seeds in the Harvester and the soil stays planted.

## Lumberjack

A Lumberjack is a pedestal (rings of a cut log on top) that fells trees: every interval it cuts **one whole tree** whose trunk stands in its area — trunk, branches, roots and leaves, plus fruit, sap or snow hanging from it — and puts the drops straight into its storage (logs from the trunk, sticks and sap from branches, fibre from leaves, as if broken by hand). Nothing is left floating and nothing drops on the ground. The tree is only cut when all of its drops fit in the storage; otherwise it waits.

| Tier | Block | Area | Interval | Slots | Recipe (Workbench) |
|---|---|---|---|---|---|
| 1 | Copper Lumberjack | 7×7×3 | 30 s | 9 | 1× Copper Hatchet + 12× Copper Bar + 10× any wood trunk |
| 2 | Iron Lumberjack | 9×9×3 | 20 s | 18 | 1× Copper Lumberjack + 15× Iron Bar |
| 3 | Thorium Lumberjack | 13×13×3 | 15 s | 27 | 1× Iron Lumberjack + 15× Thorium Bar |
| 4 | Cobalt Lumberjack | 17×17×3 | 10 s | 36 | 1× Thorium Lumberjack + 15× Cobalt Bar |

- **Which trees:** only structures that look like natural trees: wood with leaves (at least one leaf per four wood blocks) whose rooted trunk stands on soil. **Dead (leafless) trees and trees whose roots end on rock or over air are left standing** (`inspect` counts them as `notTree`). A tree counts when one of its wood blocks is in the area (a square of side `2R+1` around the Lumberjack, one block below to one block above it); its canopy may reach outside. Wood is what the game lists as tree wood (`BlockTypeList/TreeWood`), leaves are the game's tree leaves (`TreeLeaves`) and any block tagged `Family=Leaves`, so asset packs can extend both. **Apple trees and bamboo are never cut** (the Harvester picks the apples). Trees whose canopies touch are cut one at a time: each leaf goes with the tree whose wood is nearest (through leaves), so the neighbour keeps its side of the canopy.
- **Growing trees are left alone:** a sapling the game is still growing into a tree (it keeps its growth state on the trunk) is skipped until it is fully grown.
- **Big trees:** a tree with more than `Lumberjack.MaxTreeBlocks` blocks (2048 by default), or reaching more than 24 blocks sideways or 64 up from where it was found, is left standing — the Lumberjack never cuts part of a tree.
- **Replanting:** put saplings in the Lumberjack. After a cut it fills the hole the tree's roots left in the ground with the soil next to each block (grass, dirt…; air where there is none) and plants one sapling where the trunk stood — the same species when it has it (`Wood_Oak_*` → `Plant_Sapling_Oak`), else the sapling in its lowest slot — if the soil below holds saplings, there is no tilled soil within 3 blocks of it (the grown tree's roots would eat into the field) and the spot and the ring of blocks around it are free (grass tufts or flowers next to the trunk block it). Saplings are kept out of the [export](#export-to-an-adjacent-container) up to `PlanterSeedStock` (64) of each kind. Saplings come from the Farming Bench (Life Essence, which the Harvester collects).
- Export to an adjacent container, `inspect` and `perf` work as for the Harvester.

## Configuration

The settings are global (the same for the whole server) and stored in `HyPedestals.json` in the plugin's data folder (on a dedicated server: `mods/Tchorizo_HyPedestals/HyPedestals.json`). The file is created with the defaults on the first start.

```json
{
  "Copper":  { "Radius": 2, "IntervalSeconds": 12.0, "WateringSeconds": 21600 },
  "Iron":    { "Radius": 3, "IntervalSeconds": 8.0,  "WateringSeconds": 43200 },
  "Thorium": { "Radius": 4, "IntervalSeconds": 5.0,  "WateringSeconds": 72000 },
  "Cobalt":  { "Radius": 5, "IntervalSeconds": 3.0,  "WateringSeconds": 108000 },
  "CollectAnyItem": true,
  "CollectMargin": 2,
  "SoilSweepSeconds": 30,
  "ExportToAdjacent": true,
  "HarvestParticles": true,
  "PostHarvestCollectSeconds": 3.0,
  "MaxHarvestsPerCycle": 0,
  "UpgradeParticlesSeconds": 0.0,
  "ManurePerApplication": 2,
  "ManureStock": 100,
  "PlanterSeedStock": 64,
  "SaplingSpacing": 4,
  "Lumberjack": {
    "Copper":  { "Radius": 3, "IntervalSeconds": 30.0 },
    "Iron":    { "Radius": 4, "IntervalSeconds": 20.0 },
    "Thorium": { "Radius": 6, "IntervalSeconds": 15.0 },
    "Cobalt":  { "Radius": 8, "IntervalSeconds": 10.0 },
    "MaxTreeBlocks": 2048,
    "MaxBlocksPerCycle": 4096
  }
}
```

| Key | Range | Meaning |
|---|---|---|
| `<Tier>.Radius` | 1–8 | Harvest area radius R (area `2R+1` × `2R+1`) |
| `<Tier>.IntervalSeconds` | 0.5–600 | Seconds between cycles |
| `<Tier>.WateringSeconds` | 60–604800 | How long (in game seconds) the Watering upgrade keeps a soil block watered |
| `CollectAnyItem` | true/false | `true`: collect every dropped item around the area. `false`: leave tools, weapons and armor (items whose asset has a `Tool`, `Weapon` or `Armor` section) and unknown items on the ground, e.g. gear a player dropped by the farm; crops, seeds and other materials are still collected |
| `CollectMargin` | 0–4 | Extra blocks beyond the area (horizontally) where drops are still collected |
| `SoilSweepSeconds` | 5–600 | Real seconds between two soil passes of a Harvester with the Watering or the Fertilizer upgrade |
| `ExportToAdjacent` | true/false | `true`: after each cycle the Harvester moves its items into a container touching it (see [Export](#export-to-an-adjacent-container)) |
| `HarvestParticles` | true/false | A short puff of dust above the Harvester when a cycle harvests something |
| `PostHarvestCollectSeconds` | 0–10 | After a cycle that harvested, the Harvester collects drops on every tick for this many seconds (the game registers new drops with a delay of a few seconds); 0 turns it off, and drops then wait for the next cycle. The burst stops early when the storage is full, or after 3 ticks in a row that found items but could put none in |
| `MaxHarvestsPerCycle` | 0–1000 | Most crops one Harvester harvests per cycle; 0 (the default) = no limit, every ripe crop. A safety cap for servers that want one; with a cap, each cycle continues the area scan where the previous one stopped |
| `UpgradeParticlesSeconds` | 0–60 | 0 (the default) = off: only the block's own particles show the upgrades. Above 0, a Harvester with upgrades also sends its upgrade particles to the players within 75 blocks every this many seconds (each burst lasts that long); enable it if the block's own particles do not show on your client |
| `ManurePerApplication` | 0–64 | Poop the Fertilizer upgrade burns per soil block, before it uses Fertilizer bags; 0 turns poop off (bags only, and poop is exported like any other item) |
| `ManureStock` | 0–1000 | Most Poop a Harvester with the Fertilizer upgrade keeps as fuel (100 by default, one stack); the Poop above it is exported like any other item, from the highest slot down, so manure collected from livestock does not fill the storage. 0 keeps none |
| `PlanterSeedStock` | 0–1000 | With the Planter upgrade, most items of each seed or sapling kind the Harvester keeps out of the export (64 by default); the rest is exported, from the highest slot down. 0 keeps none |
| `SaplingSpacing` | 0, 2–16 | Grid spacing of the saplings the Planter upgrade plants (4 by default); 0 = no saplings |
| `Lumberjack.<Tier>.Radius` | 1–16 | Lumberjack area radius R |
| `Lumberjack.<Tier>.IntervalSeconds` | 1–600 | Seconds between two Lumberjack cycles (one tree each) |
| `Lumberjack.MaxTreeBlocks` | 64–8192 | Largest tree a Lumberjack cuts, in blocks (wood, leaves and what hangs from them); bigger trees are left standing (and not searched again for 20 cycles) |
| `Lumberjack.MaxBlocksPerCycle` | 256–65536 | Most blocks the tree searches of one Lumberjack cycle read (4096 by default); the next cycle goes on where it stopped |

Out-of-range values in the file are replaced by their defaults at startup (one warning each). Edits to the file are read when the server starts; changes made in game apply to every Harvester from its next cycle.

In game (operators):

- `/hypedestals config` — opens the settings window: radius, interval and watering duration for each tier and the global options. **Save** applies the fields you changed since the window opened (values changed meanwhile by someone else, e.g. with `config set`, are kept) and writes the file, **Reset defaults** fills in the default values (press Save to apply them), **Close** (or Esc) closes without saving. From the server console it prints the values instead.
- `/hypedestals config show` — prints the current values and the file path.
- `/hypedestals config set <Copper|Iron|Thorium|Cobalt> <Radius|IntervalSeconds|WateringSeconds> <value>` and `/hypedestals config set global <CollectAnyItem|CollectMargin|SoilSweepSeconds|ExportToAdjacent|HarvestParticles|PostHarvestCollectSeconds|MaxHarvestsPerCycle|UpgradeParticlesSeconds|ManurePerApplication|ManureStock|PlanterSeedStock|SaplingSpacing> <value>`, `/hypedestals config set Lumberjack.<Copper|Iron|Thorium|Cobalt> <Radius|IntervalSeconds> <value>` and `/hypedestals config set Lumberjack <MaxTreeBlocks|MaxBlocksPerCycle> <value>` — change one value, apply it and save the file. Also works from the server console.

## Export to an adjacent container

With `ExportToAdjacent` on (the default), at the end of every cycle (also when the storage is full) the Harvester moves its items into a storage block touching it: below first, then the four sides (+X, −X, +Z, −Z; never above). Each stack goes to the first container with room; what does not fit stays in the Harvester for the next cycle. Chests and other blocks with the game's item storage work; another Harvester or a Lumberjack is never a target, so machines placed side by side do not pass items around. The Lumberjack exports the same way and keeps up to `PlanterSeedStock` saplings of each kind. With the Fertilizer upgrade installed, **Fertilizer bags are never exported** and **Poop is kept up to `ManureStock`** (100 by default), whatever their slot (they are the upgrade's supply; Poop only while `ManurePerApplication` is above 0): the Poop above that stock leaves, from the highest slot down. With the Planter upgrade installed, **each seed and sapling kind is kept up to `PlanterSeedStock`** (64 by default) the same way. Without the upgrades, all of these are exported like any other item.

## Compatibility

- **ModernStorage:** an Import Bus placed against a Harvester (front facing it) pulls the harvest into the storage network, with no setup on the Harvester side. An Export Bus filtered to Fertilizer should be able to keep a Fertilizer upgrade supplied, and an Import Bus may pull the bags back out (both not tested yet).
- **AutoStorage:** it only pushes items out of its own chest and never pulls from other containers, so place a Harvester right next to an AutoStorage chest and let [export](#export-to-an-adjacent-container) move the harvest into it.
- **Chests and other containers** that use the game's item storage work as export targets.
- **LifeCrops:** its PoopCrop is a crop like any other, so a row of PoopCrop inside the Harvester's area can keep the Fertilizer upgrade supplied with poop (not tested yet).
- **Eternal crops** (vanilla) are harvested and grow back on their own; regular annual crops are replanted by the Planter Upgrade (or by you).
- **LifeCrops:** with the Planter Upgrade its seeds follow its own soil rules (on its essence soil they place the essence variant, as by hand).

## Admin commands

Operators, in chat (with `/`) or from the server console (without it):

| Command | What it does |
|---|---|
| `/hypedestals inspect <x y z>` | Shows the Harvester or Lumberjack at that position (see below) |
| `/hypedestals config` | Opens the settings window (from the console: prints the values) |
| `/hypedestals config show` | Prints the current settings and the file path |
| `/hypedestals config set …` | Changes one setting, applies it and saves the file (see [Configuration](#configuration)) |
| `/hypedestals perf` | Shows how much server time the Harvesters and Lumberjacks use, per block type and in total (see below) |
| `/hypedestals perf reset` | Starts a new `perf` measurement |

Permission nodes (operators hold them all through the `*` wildcard; nobody else does by default): `tchorizo.hypedestals.command.hypedestals`, plus `.inspect`, `.config`, `.config.show`, `.config.set`, `.perf` and `.perf.reset` below it. A subcommand also needs the nodes of its parent commands, so letting a non-operator use `config set` takes `tchorizo.hypedestals.command.hypedestals`, `tchorizo.hypedestals.command.hypedestals.config` and `tchorizo.hypedestals.command.hypedestals.config.set`.

`inspect` prints the tier, radius, interval, seconds until the next cycle, where the next harvest scan starts (`scan=<position>/<total>`, saved with the block), used/total slots, the last cycle (`harvested`, `collected`, `containerFull` — `true (crops waiting)` when the cycle stopped at a crop whose drops did not fit, so it and the rest of the area wait ripe on the plant and the next cycle starts there; plain `true` when the storage had no room left at the end of the cycle —, `unloaded=<n>` when that many positions of the area were in a chunk that was not loaded and were skipped, `exported=<items>→<x y z>` with the container that received them, `→none` when nothing moved), the seconds left of the post-harvest collect burst (`burst=<s>`), the installed `upgrades=[…]`, the soil passes since the chunk loaded (`soil: watered=… total=… fertilized=… fertilizedTotal=… lastSweep=…s`, or `none` before the first one) and, with the Fertilizer upgrade, the fuel left (`fertilizer=poop <n> (K=<ManurePerApplication>) + bags <uses>`, or `none` when the storage holds neither poop nor a usable Fertilizer bag). At the end, `perf: last=<us> p50=<us> p95=<us> max=<us> (n=<cycles>)` gives that Harvester's cycle durations in microseconds since its chunk loaded (percentiles over its last 64 cycles). It loads the section from disk if it exists and never generates terrain; with nothing to inspect it replies `no Harvester at <x y z>`, or `section not loaded at <x y z>` when that part of the world has never been generated.

For a Lumberjack, `inspect` prints `lumberjack tier= radius= interval= next= scan= slots= saplings=` and the last cycle: `cut=<blocks> trees=<0|1> items=<n> replant=<planted|no-sapling|no-room|none>` (with `sapling=<id>`), what it skipped (`growing=`, `tooBig=`, `notTree=` for wood that does not look like a natural tree, `unloaded=`), `budgetHit=true` when it stopped at `Lumberjack.MaxBlocksPerCycle`, `containerFull=` (`true (trees waiting)`) and `exported=`, then `treesTotal=` and the cycle timings.

`perf` measures the time the Harvesters and Lumberjacks spend on the world thread (every tick, timed with the JVM's nanosecond clock; the cost of measuring is negligible) since the first machine ticked after the server started, or since the last `perf reset`, and prints one line per block type and tier (`Harvester Copper`, `Lumberjack Copper`, …) and a total: machines of that type seen in the last minute (`harvesters=`, `lumberjacks=`; the total says `machines=`), cycles (and cycles per second), crops harvested or trees cut (`harvested=`, `trees=`), the mod's time per real second (`us/s`) with its share of the world thread and per tick (a Hytale world runs 30 ticks per second, 33.3 ms per tick), the p50/p95/max cycle duration over the last 512 cycles, and the time per phase (`scan` = reading the area and harvesting, `collect` = the cycle's two collects, `burst` = the post-harvest collect burst, `soil` = watering and fertilizer passes, `export`, `visual` = the upgrade block state). The last line names the Harvester with the worst p95 among them. Work the game does for the mod after the tick (applying block changes, spawning drops) is not included; the vanilla `/world perf` shows the whole tick.

## Known limitations

- **Lumberjacks only cut structures that look like natural trees** (leaves, standing on soil); keep them away from log builds with leaf roofs. They do not check claims either.
- **No claim/protection support.** Harvesting goes through the game's farming code, which does not fire a block break event, so claim or protection plugins cannot stop a Harvester from harvesting inside a protected area. Watering, fertilizing and export do not check claims either.
- **Only works while its chunk section is loaded and ticking**, i.e. near a player. There is no chunk loading: the part of an area that reaches into a neighbouring chunk that is not loaded is skipped until that chunk loads (`inspect` shows `unloaded=<n>`).
- **Vacuums any dropped item** within radius + `CollectMargin` (2 by default), not only crops, unless `CollectAnyItem` is turned off (then tools, weapons and armor stay on the ground).
- **Overlapping Harvesters compete.** Where areas overlap, whichever Harvester runs first takes the crop, and each dropped item in the overlap goes to whichever Harvester collects it first. Harvesters whose areas only touch (e.g. two Iron 7 blocks apart) keep their own drops: a Harvester leaves the items lying inside another active Harvester's area to that one, even within its own collect margin. A drop that rolls over the border into the neighbour's area goes to the neighbour.
- **Annual crops are only replanted with the Planter Upgrade** and seeds in the Harvester's storage; without it they are removed when harvested. Bushes that reset after harvest (e.g. berries) regrow by themselves.
- **Crops wait on the plant when storage is full.** The Harvester stops harvesting once the drops of the next ripe crop would not fit and only resumes when space frees up; keep an export target with room for a farm that never stalls. The room check starts from the most each crop can drop and, after a few harvests of a crop type, uses 1.5 × what that type was seen to drop (never more than the maximum), so crops whose drop list allows far more than they usually give do not hold space they never use. A harvest that drops more than that leaves the excess on the ground until the next collect, and sends its crop type back to the maximum. Items that other sources drop in the collect area (e.g. a player) take space like any other.
- If harvesting a crop type keeps failing with an error, that Harvester skips that crop type until its chunk reloads (one warning is logged). A crop the game is still updating is simply retried on the next cycle.
- **Creative mode:** installing an upgrade does not use up the item, and removing one still gives an item back, so upgrade items can be multiplied in Creative (like any item there).
- **No owner checks.** A Harvester belongs to nobody: any player can open its storage, install upgrades, or crouch + Use to take its upgrades out, including on someone else's Harvester.
- Crops planted one block below the Harvester's level have their soil outside the area, so the upgrades do not reach it.
- Remove your Harvesters before uninstalling the plugin.

## Install

1. Download `HyPedestals-<version>.jar` from the [releases](https://github.com/Tchorizo/HyPedestals/releases).
2. Put it in:
   - **Singleplayer:** `%APPDATA%\Hytale\UserData\Mods`
   - **Dedicated server:** `<server>/mods/`
3. Start the game or server.

## Build

Requires JDK 25.

```bash
./gradlew build
```

The plugin jar is written to `build/libs/`. The Hytale server API (`com.hypixel.hytale:Server:0.7.2`) is resolved from `https://maven.hytale.com/release` at compile time only; the jar has no bundled dependencies.

## Support

HyPedestals is free and always will be. If you enjoy it, you can [buy me a coffee on Ko-fi](https://ko-fi.com/tchori). Bugs and ideas go to the [issues](https://github.com/Tchorizo/HyPedestals/issues).

I build my mods with AI assistance: every change is reviewed and tested on a real server, and all the source code is in this repository. The textures and icons are AI-generated.

## License

[GPL-3.0](LICENSE).

HyPedestals is a fan project and is not affiliated with or endorsed by Hypixel Studios.
