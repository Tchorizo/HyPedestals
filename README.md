# HyPedestals

A Hytale server plugin that adds **Harvester** blocks: place one next to your crops and it harvests every ripe crop in range, storing the drops in its own inventory. Four material tiers (Copper, Iron, Thorium, Cobalt) — higher tiers cover a larger area, work faster and hold more items.

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

The numbers live in the block's asset JSON (`HyHarvester` and `ItemContainerBlock` components), so server owners can rebalance them with an asset override.

## Usage

1. Craft a Harvester at a Workbench.
2. Place it next to (or in the middle of) your crops.
3. Every interval it first collects dropped items within its radius + 1 into its storage, then harvests ripe crops in its area. The drops are picked up on the next cycle.
4. Right-click the Harvester to open its storage.

When the storage is full, the Harvester stops harvesting (nothing is harvested onto the ground) until a slot frees up.

### Admin command

`/hypedestals inspect <x y z>` — shows the Harvester at that position: tier, radius, interval, seconds until the next cycle, used/total slots and the result of the last cycle (harvested, collected, paused).

The command never generates terrain. If there is nothing to inspect it replies `no Harvester at <x y z>`, or `section not loaded at <x y z>` when that part of the world has never been generated.

## Known limitations

- **No claim/protection support.** Harvesting goes through the game's farming code, which does not fire a block break event, so claim or protection plugins cannot stop a Harvester from harvesting inside a protected area.
- **Only works while its chunk section is loaded and ticking**, i.e. near a player. There is no chunk loading.
- **Annual crops are not replanted.** They are removed when harvested; replant them yourself. Bushes that reset after harvest (e.g. berries) regrow by themselves.
- **One harvest can produce up to 2 stacks** (the crop plus Life Essence). When the storage is nearly full, whatever does not fit stays on the ground and is collected once space frees up.
- **Vacuums any dropped item** within radius + 1, not only crops.
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

## License

[GPL-3.0](LICENSE).

HyPedestals is a fan project and is not affiliated with or endorsed by Hypixel Studios.
