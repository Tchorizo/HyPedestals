# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added

- Global configuration file `HyPedestals.json` in the plugin's data folder: `Radius`, `IntervalSeconds` and `WateringSeconds` per tier, plus `CollectAnyItem` (turn off to leave tools, weapons and armor on the ground), `CollectMargin` (how far beyond the area drops are collected), `SoilSweepSeconds`, `ExportToAdjacent` and `HarvestParticles`. The shipped tier values are the defaults; the block asset's `Radius`/`IntervalSeconds` are only used for tiers the config does not know. Out-of-range values in the file fall back to their defaults with a warning.
- `/hypedestals config show` and `/hypedestals config set …` to view and change the configuration in game or from the console, and an in-game settings window (`/hypedestals config`, operators; English and Brazilian Portuguese) that saves only the fields you changed. Changes apply from each Harvester's next cycle.
- Export to an adjacent container (`ExportToAdjacent`, on by default): after each cycle, also when the storage is full, the Harvester moves its items into a storage block below it or on one of its four sides, each stack to the first container with room. Harvesters never export to each other and the last slot is kept.
- Upgrade items **Watering Upgrade** and **Fertilizer Upgrade**, crafted at the Farming Bench (tier 2 and 3). Right-click a Harvester while holding one to install it (one of each per Harvester; in Creative the item is not used up); crouch + Use (F) takes the most recently installed upgrade back into your inventory (or drops it at your feet when it is full); plain Use still opens the storage.
- Watering upgrade: on its own timer (`SoilSweepSeconds`, 30 s by default, so also while the storage is full) the Harvester waters the tilled soil in its area for its tier's `WateringSeconds` of game time, rewatering only where less than half of that is left.
- Fertilizer upgrade: the Harvester's last storage slot becomes a fertilizer input that only accepts the vanilla Fertilizer bag, and the harvest never goes there. On the same soil pass the Harvester fertilizes each tilled soil block in its area that has a crop on it and is not fertilized yet, one bag use per block; a used-up bag disappears.
- Breaking a Harvester drops its installed upgrades where it stood; unloading the chunk or restarting the server keeps them installed.
- Installed upgrades are visible: blue sparks with the watering upgrade, green spores with the fertilizer, both with both. With `HarvestParticles` on (the default) a short puff of dust rises from the Harvester when a cycle harvests something.
- `/hypedestals inspect` shows where the next harvest scan starts (`scan=<position>/<total>`), the last export (`exported=<items>→<x y z>`, or `→none`), the installed `upgrades=[…]`, the soil passes (`soil: watered=… fertilized=…`) and, with the fertilizer upgrade, the bag in the input slot (`fertilizer=<uses>/<max>`, `none` or `blocked`).

### Changed

- Harvester blocks are now shaped like a pedestal (base, column, top plate and wheel) instead of a plain cube, with matching item icons.
- Dropped items are collected from a box reaching `CollectMargin` blocks beyond the harvest area (and from below and above it), so drops that roll away are still picked up, and a second time right after harvesting, so part of each cycle's drops reaches the storage in the same cycle.
- Use (F) on a Harvester goes through a crouch check: standing opens the storage as before, crouching removes an upgrade.

### Fixed

- A Harvester whose storage had few empty slots kept harvesting the same one or two crops every cycle: partially filled stacks now count as room, and each cycle continues the area scan where the previous one stopped.
- Tall ripe crops (e.g. wheat) are harvested only from their bottom block; harvesting the top half could remove an Eternal crop instead of resetting it.
- A crop that the game is still updating (just ripened) no longer makes the Harvester skip that crop type until the chunk reloads; it is retried on the next cycle.

## 0.1.0 — 2026-10-16

### Added

- Copper, Iron, Thorium and Cobalt Harvester blocks, crafted at the Workbench; each tier is crafted from the previous one.
- Automatic harvesting of ripe crops in the Harvester's area, with the drops collected into its storage (right-click to open).
- Harvesting pauses while the storage is full.
- `/hypedestals inspect <x y z>` admin command showing a Harvester's settings, timer, storage usage and last cycle.
- English and Brazilian Portuguese translations.
