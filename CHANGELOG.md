# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Fixed

- The Lumberjack did not replant where grass tufts stood within a block of the stump (`replant=no-room`; common in forests). It now clears the grass tufts (`Family=Grass`) from the sapling's space before planting — flowers, ferns, mushrooms and anything else still block.
- The Lumberjack never cut mature trees with logs where the ground is a thin layer of soil over rock (most of a rocky biome): their roots go through the soil down to the stone, and the Lumberjack took a trunk standing on stone for a log build (`notTree` in `/hypedestals inspect`). It only cut the small young birches, whose trunk is made of branch blocks and drops sticks, fibre and sap but no logs, so a Lumberjack next to a forest filled up with sticks and never with logs. A tree whose roots are sunk at least two blocks into the ground is now a tree whatever is under them; log cabins, pillars and leaf roofs on a stone floor are still left alone.

## 0.3.0 — 2026-10-21

### Added

- **Planter Upgrade** (Farming Bench tier 2: 1× Iron Hoe + 4× Iron Bar + 25× Life Essence). After every harvest cycle the Harvester plants the seeds from its storage (any slot, lowest slot first) on every empty block of its area whose soil the seed grows on — the seed's own planting rule, read from the game's seed interaction, so vanilla seeds go on tilled soil, herbs on crystal soil and seeds of mods that follow the vanilla seed format on their own soils — and replants annual crops in the same cycle. Tree saplings are planted on grass or other non-tilled soil on a grid every `SaplingSpacing` blocks from the Harvester, only where the space around them is free. With the upgrade the export keeps up to `PlanterSeedStock` of each seed and sapling kind. Amber orbs over the Harvester show it; a Harvester now holds up to three upgrades.
- Planter and Lumberjack saplings are not planted within 3 blocks of tilled soil.
- `PlanterSeedStock` (global, 0–1000, 64 by default) and `SaplingSpacing` (global, 0 or 2–16, 4 by default; 0 = no saplings), in the config file, the settings window and `config set global`.
- `/hypedestals inspect`: `planter: planted=<seeds>+<saplings> total=<seeds>+<saplings> seeds=<n>` with the Planter upgrade.
- **Lumberjack** block in four tiers (Copper, Iron, Thorium, Cobalt; Workbench: 1× Copper Hatchet + 12× Copper Bar + 10× any wood trunk, then the previous tier + 15 bars). Every interval it cuts one whole tree whose wood stands in its area — trunk, branches, roots, leaves and what hangs from them, nothing left floating — and puts the drops (logs, sticks, sap, fibre) straight into its storage, only when they all fit. It fills the hole the roots left with soil and plants a sapling from its storage where the trunk stood (same species first). Only structures that look like natural trees (leaves, standing on soil) are cut, so log builds are safe unless they have leaf roofs on soil; growing saplings, apple trees and bamboo are left alone; trees bigger than `Lumberjack.MaxTreeBlocks` are left standing. Exports like the Harvester, keeping up to `PlanterSeedStock` saplings of each kind.
- `Lumberjack.<Tier>.Radius` (1–16), `Lumberjack.<Tier>.IntervalSeconds` (1–600) `Lumberjack.MaxTreeBlocks` (64–8192, 2048 by default) and `Lumberjack.MaxBlocksPerCycle` (256–65536, 4096 by default) in the config file, the settings window and `config set Lumberjack[.<Tier>]`.
- `/hypedestals inspect` shows a Lumberjack (`lumberjack tier=… last: cut=… trees=… replant=… notTree=…`); `/hypedestals perf` has one line per block type and tier (`Harvester Copper`, `Lumberjack Copper`, …) and a `machines=` total.

## 0.2.3 — 2026-10-20

### Fixed

- Two Harvesters whose areas overlap could crash the world (`IllegalArgumentException: Duplicate block components`; the server reloads the world and disconnects the players in it) when both ran their cycle in the same server tick and harvested the same ripe crop. Harvesters of the same tier loaded together (e.g. in the same chunk at server start) always ran their cycles in the same tick. A crop is now harvested by only one Harvester per tick, and each Harvester starts its cycle timer at an offset taken from its position, so neighbours no longer run their cycles together after a load (the first cycle after a load or placement still comes within one interval).

## 0.2.2 — 2026-10-20

### Added

- **Animal manure fuels the Fertilizer upgrade.** Poop (`Ingredient_Poop`) in the Harvester's storage, from any slot, is used before Fertilizer bags: `ManurePerApplication` poop per soil block (global, 0–64, 2 by default, the same poop per use as a crafted bag; 0 = bags only; in the config file, the settings window and `config set global`). Poop from several slots adds up; a rest below that number waits for more. Keep livestock inside the fenced field and the Harvester collects their poop.
- `ManureStock` (global, 0–1000, 100 by default = one stack; in the config file, the settings window and `config set global`): with the Fertilizer upgrade installed (and `ManurePerApplication` above 0) the Harvester keeps up to that much poop as fuel and exports the rest, from the highest slot down, so a field with livestock does not fill it with poop.
- `UpgradeParticlesSeconds` (global, 0–60, **0 = off, the default**; in the config file, the settings window and `config set global`): above 0, a Harvester with upgrades also sends its upgrade particles to the players within 75 blocks every this many seconds, each burst lasting until the next one. Off by default; enable it if the block's own upgrade particles do not show on your client.
- Harvesters also pick ripe apples on apple trees, which regrow.

### Fixed

- The upgrade particles could not be seen in game: the watering sparks (the mana plant sparks, at most 4 tiny particles within 0.2 blocks of the block's center) most likely stayed inside the pedestal's shaft, under its cap. Both upgrades now show glowing orbs (the vanilla temple lantern effect, tinted) on a ring over the pedestal, just above its cap: saturated blue for watering, bright green for the fertilizer, both with both (the two rings at slightly different heights so they do not overlap).
- The Harvester no longer harvests tree saplings, cacti or spreading mushrooms (which it could destroy or stop from growing), nor tilled soil itself. A grown cactus was harvested and lost the part that keeps it growing.
- A Harvester whose storage was nearly full could keep harvesting only the first positions of its area. When a crop's drops did not fit, the next cycle was meant to resume at that crop, but that position was forgotten whenever the chunk reloaded, so the harvest started over at the same corner while the rest of the field stayed ripe. The position is now saved with the block and survives chunk reloads and restarts.
- Harvesters a few blocks apart (e.g. two Iron 7 blocks apart) no longer collect each other's drops: a Harvester leaves the items lying inside another active Harvester's area to that one, even within its own collect margin (a broken Harvester's area is released at once). Before, whichever collected first took the drops along the shared border.
- `/hypedestals inspect` says why ripe crops are not being picked: `containerFull=true (crops waiting)` when the cycle stopped at a crop whose drops did not fit (the crop and the rest of the area wait on the plant until the storage has room), and `unloaded=<n>` when part of the area lies in a chunk that is not loaded (those positions are skipped until it is).

### Changed

- A Harvester harvests every ripe crop **only while its storage has room for its reservation** (see Known limitations in the README): before each crop it checks that the crop's reservation still fits (counting what the cycle already harvested, and what the last two cycles harvested that has not reached the storage yet), and the first crop that does not fit ends the cycle; it and the rest stay ripe on the plant until space frees up (0.2.1 kept harvesting into a pile on the ground that despawned after 600 s). The reservation starts at the most the crop can drop; after a few harvests of a crop type it is 1.5 × what that type was seen to drop (never more than the maximum; at least one of each item it can drop), learned per crop type while the server runs, so crops whose drop list allows far more than they usually give (e.g. the LifeCrops Eternal crop: up to 37 items, about 7 per harvest) do not hold room they never use. A cycle whose harvests of a type drop all of that reservation or more leaves any excess for the next collect and sends the type back to the maximum. A crop whose harvest drop list cannot be found counts as dropping a full stack of its item, with a warning in the log once per crop.
- While the storage is full the Harvester skips both collects of its cycle and the post-harvest collect burst; the burst also stops after 3 ticks in a row that found items but could put none in. This removes the server-time cost of a full Harvester trying to collect a pile around it.
- `/hypedestals inspect`: `containerFull=true` also when the cycle stopped at a crop whose drops did not fit; `fertilizer=poop <n> (K=<ManurePerApplication>) + bags <uses>` (or `none`) instead of the next bag's durability and slot.
- `/hypedestals perf`: `harvesters=` and the worst Harvester only count Harvesters that ran a cycle in the last minute; unloaded or removed ones are forgotten.

## 0.2.1 — 2026-10-17

### Changed

- New emblem on top of the Harvesters: a wheel with eight straight spokes in the tier's metal. The previous curved-blade wheel could look like an offensive symbol when seen from above, so it was replaced everywhere (block textures, item icons and the mod icon).
- The mod icon in the world's mod list now shows the pedestal-shaped Harvester instead of the old cube.

## 0.2.0 — 2026-10-17

### Added

- Global configuration file `HyPedestals.json` in the plugin's data folder: `Radius`, `IntervalSeconds` and `WateringSeconds` per tier, plus `CollectAnyItem` (turn off to leave tools, weapons and armor on the ground), `CollectMargin` (how far beyond the area drops are collected), `SoilSweepSeconds`, `ExportToAdjacent`, `HarvestParticles` and `MaxHarvestsPerCycle` (optional cap on harvests per cycle, 0–1000, 0 = no limit, the default). The shipped tier values are the defaults; the block asset's `Radius`/`IntervalSeconds` are only used for tiers the config does not know. Out-of-range values in the file fall back to their defaults with a warning.
- `/hypedestals config show` and `/hypedestals config set …` to view and change the configuration in game or from the console, and an in-game settings window (`/hypedestals config`, operators; English and Brazilian Portuguese) that saves only the fields you changed. Changes apply from each Harvester's next cycle. `config set` replies `saved` only once the file is written. All `/hypedestals` commands are operator-only, with explicit permission nodes under `tchorizo.hypedestals.command.hypedestals`.
- Export to an adjacent container (`ExportToAdjacent`, on by default): after each cycle, also when the storage is full, the Harvester moves its items into a storage block below it or on one of its four sides, each stack to the first container with room. Harvesters never export to each other, and Fertilizer bags stay while the Fertilizer upgrade is installed.
- Upgrade items **Watering Upgrade** and **Fertilizer Upgrade**, crafted at the Farming Bench (tier 2 and 3). Right-click a Harvester while holding one to install it (one of each per Harvester; in Creative the item is not used up); crouch + Use (F) takes the most recently installed upgrade back into your inventory (or drops it at your feet when it is full); plain Use still opens the storage.
- Watering upgrade: on its own timer (`SoilSweepSeconds`, 30 s by default, so also while the storage is full) the Harvester waters the tilled soil in its area for its tier's `WateringSeconds` of game time, rewatering only where less than half of that is left.
- Fertilizer upgrade: on the same soil pass the Harvester fertilizes each tilled soil block in its area that has a crop on it and is not fertilized yet, one use of a Fertilizer bag from its storage per block; a used-up bag disappears.
- Breaking a Harvester drops its installed upgrades where it stood; unloading the chunk or restarting the server keeps them installed.
- Installed upgrades are visible: blue sparks with the watering upgrade, green spores with the fertilizer, both with both. With `HarvestParticles` on (the default) a short puff of dust rises from the Harvester when a cycle harvests something.
- Post-harvest collect burst (`PostHarvestCollectSeconds`, 3 s by default, 0–10, 0 = off): after a cycle that harvested something the Harvester collects drops on every tick for that long, so the harvest reaches its storage within the same cycle instead of the next one; `/hypedestals inspect` shows the time left as `burst=<s>`.
- `/hypedestals inspect` shows whether the storage was full at the end of the last cycle (`containerFull=true|false`, replacing `paused`), where the next harvest scan starts (`scan=<position>/<total>`), the last export (`exported=<items>→<x y z>`, or `→none`), the installed `upgrades=[…]`, the soil passes (`soil: watered=… fertilized=…`) and, with the fertilizer upgrade, the bag the next pass uses (`fertilizer=<uses>/<max> (slot N)` or `none`).

- `/hypedestals perf` (operators): world-thread time the Harvesters use, per tier and in total, since the server started or the last `/hypedestals perf reset` — the mod's time per second and its share of a tick, cycles per second, harvests, p50/p95/max cycle duration, time per phase (scan, collect, burst, soil, export, visual) and the Harvester with the worst p95. `/hypedestals inspect` ends with that Harvester's own cycle timings (`perf: last= p50= p95= max= (n=)`).

### Changed

- Each cycle harvests **every ripe crop** in the Harvester's area. The storage's free space no longer limits or pauses the harvest: what does not fit stays on the ground and is collected once room frees up (export, emptying the storage). With `MaxHarvestsPerCycle` set, each cycle continues the area scan where the previous one stopped.
- Harvester blocks are now shaped like a pedestal (base, column, top plate and wheel) instead of a plain cube, with matching item icons.
- Dropped items are collected from a box reaching `CollectMargin` blocks beyond the harvest area (and from below and above it), so drops that roll away are still picked up, and a second time right after harvesting, so part of each cycle's drops reaches the storage in the same cycle.
- Use (F) on a Harvester goes through a crouch check: standing opens the storage as before, crouching removes an upgrade.
- Fertilizer upgrade: the bags can sit in any slot of the Harvester's storage (the most used one is spent first) instead of a reserved last slot that only accepted Fertilizer; the harvest can use every slot again. Export leaves Fertilizer bags in place while the upgrade is installed.

### Fixed

- Tall ripe crops (e.g. wheat) are harvested only from their bottom block; harvesting the top half could remove an Eternal crop instead of resetting it.
- A crop that the game is still updating (just ripened) no longer makes the Harvester skip that crop type until the chunk reloads; it is retried on the next cycle.

## 0.1.0 — 2026-10-16

### Added

- Copper, Iron, Thorium and Cobalt Harvester blocks, crafted at the Workbench; each tier is crafted from the previous one.
- Automatic harvesting of ripe crops in the Harvester's area, with the drops collected into its storage (right-click to open).
- Harvesting pauses while the storage is full.
- `/hypedestals inspect <x y z>` admin command showing a Harvester's settings, timer, storage usage and last cycle.
- English and Brazilian Portuguese translations.
