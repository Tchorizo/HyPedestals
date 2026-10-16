# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added

- Global configuration file `HyPedestals.json` (plugin data folder): radius and interval per tier, `CollectAnyItem` (turn off to leave tools, weapons and armor on the ground) and `CollectMargin` (how far beyond the area drops are collected). The shipped tier values are the defaults; the block asset's `Radius`/`IntervalSeconds` are only used for tiers the config does not know.
- `/hypedestals config show` and `/hypedestals config set …` to view and change the configuration in game or from the console; changes apply from each Harvester's next cycle.
- In-game settings window (`/hypedestals config`, operators) to edit the same values, in English and Brazilian Portuguese.
- Configuration keys `WateringSeconds` (per tier), `SoilSweepSeconds`, `ExportToAdjacent` and `HarvestParticles`, also in `config set` and the settings window.
- Export to an adjacent container (`ExportToAdjacent`, on by default): after each cycle the Harvester moves its items into a storage block below it or on one of its four sides, first container with room first; Harvesters never export to each other and the last slot is kept. `/hypedestals inspect` shows `exported=<items>`.
- Watering upgrade: with it installed, every `SoilSweepSeconds` (30 s by default, on its own timer, so also while the storage is full) the Harvester waters the tilled soil in its area for its tier's `WateringSeconds` of game time, rewatering only where less than half of that is left. `/hypedestals inspect` shows `soil: watered=<n>`.
- Fertilizer upgrade: with it installed, the Harvester's last storage slot becomes a fertilizer input that only accepts the vanilla fertilizer bag, and the harvest never goes there. On the same soil sweep, the Harvester fertilizes each tilled soil block in its area that has a crop on it and is not fertilized yet, one bag use per block (5 per bag); a used-up bag disappears. `/hypedestals inspect` shows `fertilized=<n>` and the bag's `fertilizer=<uses>/<max>` (`none` when empty, `blocked` when the slot still holds something else).
- Upgrade items `Watering Upgrade` and `Fertilizer Upgrade`, crafted at the Farming bench (tier 2 and 3). Right-click a Harvester while holding one to install it (one of each per Harvester); crouch + Use (F) on the Harvester takes the last installed upgrade back into your inventory; plain Use still opens the storage. `/hypedestals inspect` lists the installed `upgrades=[…]`.
- Breaking a Harvester drops its installed upgrades as items where it stood; unloading the chunk or restarting the server keeps them installed.
- Installed upgrades are visible: the Harvester gives off blue sparks with the watering upgrade, green spores with the fertilizer, both with both. With `HarvestParticles` on (the default) a short puff of dust rises from the Harvester when a cycle harvests something.

### Changed

- Harvester blocks are now shaped like a pedestal (base, column, top plate and wheel) instead of a plain cube, with matching item icons.
- `/hypedestals inspect` also shows where the next harvest scan starts (`scan=<position>/<total>`).

### Fixed

- A Harvester whose storage had few empty slots kept harvesting the same one or two crops every cycle: partially filled stacks now count as room, and each cycle continues the area scan where the previous one stopped.

## 0.1.0 — 2026-10-16

### Added

- Copper, Iron, Thorium and Cobalt Harvester blocks, crafted at the Workbench; each tier is crafted from the previous one.
- Automatic harvesting of ripe crops in the Harvester's area, with the drops collected into its storage (right-click to open).
- Harvesting pauses while the storage is full.
- `/hypedestals inspect <x y z>` admin command showing a Harvester's settings, timer, storage usage and last cycle.
- English and Brazilian Portuguese translations.
