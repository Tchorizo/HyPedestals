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

The numbers in the table are the defaults. Radius and interval can be changed per tier in the [configuration](#configuration); the storage size lives in the block's asset JSON (`ItemContainerBlock` component). The `HyHarvester` component's `Radius`/`IntervalSeconds` in the asset are only used for a tier the configuration does not know (e.g. one added by another asset pack); there Radius must be 1–8 and IntervalSeconds > 0, and invalid values disable that block (one warning in the server log).

## Usage

1. Craft a Harvester at a Workbench.
2. Place it next to (or in the middle of) your crops.
3. Every interval it first collects dropped items around its area into its storage, then harvests ripe crops in its area, one harvest per storage slot that can still take items. When it cannot harvest everything at once, the next cycle continues where the last one stopped, so the whole area gets harvested over a few cycles. Most drops are picked up on the next cycle.
4. Right-click the Harvester to open its storage.

When no storage slot can take more items, the Harvester stops harvesting (nothing is harvested onto the ground) until space frees up.

### Admin command

`/hypedestals inspect <x y z>` — shows the Harvester at that position: tier, radius, interval, seconds until the next cycle, where the next harvest scan starts (`scan=<position>/<total>`), used/total slots and the result of the last cycle (harvested, collected, paused).

The command loads the section from disk if it exists and never generates terrain. If there is nothing to inspect it replies `no Harvester at <x y z>`, or `section not loaded at <x y z>` when that part of the world has never been generated.

## Configuration

The settings are global (the same for the whole server) and stored in `HyPedestals.json` in the plugin's data folder (on a dedicated server: `mods/Tchorizo_HyPedestals/HyPedestals.json`). The file is created with the defaults on the first start.

```json
{
  "Copper":  { "Radius": 2, "IntervalSeconds": 12.0 },
  "Iron":    { "Radius": 3, "IntervalSeconds": 8.0 },
  "Thorium": { "Radius": 4, "IntervalSeconds": 5.0 },
  "Cobalt":  { "Radius": 5, "IntervalSeconds": 3.0 },
  "CollectAnyItem": true,
  "CollectMargin": 2
}
```

| Key | Range | Meaning |
|---|---|---|
| `<Tier>.Radius` | 1–8 | Harvest area radius R (area `2R+1` × `2R+1`) |
| `<Tier>.IntervalSeconds` | 0.5–600 | Seconds between cycles |
| `CollectAnyItem` | true/false | `true`: collect every dropped item around the area. `false`: leave tools, weapons and armor (items whose asset has a `Tool`, `Weapon` or `Armor` section) and unknown items on the ground, e.g. gear a player dropped by the farm; crops, seeds and other materials are still collected |
| `CollectMargin` | 0–4 | Extra blocks beyond the area (horizontally) where drops are still collected |

Out-of-range values in the file are replaced by their defaults at startup (one warning each). Edits to the file are read when the server starts; changes made in game apply to every Harvester from its next cycle.

In game (operators):

- `/hypedestals config` — opens the settings window: radius and interval for each tier, the collect-any-item checkbox and the collect margin. **Save** applies and writes the file, **Reset defaults** fills in the default values (press Save to apply them), **Close** (or Esc) closes without saving. From the server console it prints the values instead.
- `/hypedestals config show` — prints the current values and the file path.
- `/hypedestals config set <Copper|Iron|Thorium|Cobalt> <Radius|IntervalSeconds> <value>` and `/hypedestals config set global <CollectAnyItem|CollectMargin> <value>` — change one value, apply it and save the file. Also works from the server console.

## Known limitations

- **No claim/protection support.** Harvesting goes through the game's farming code, which does not fire a block break event, so claim or protection plugins cannot stop a Harvester from harvesting inside a protected area.
- **Only works while its chunk section is loaded and ticking**, i.e. near a player. There is no chunk loading.
- If harvesting a crop type throws an error, that Harvester skips that crop type until its chunk reloads (one warning is logged).
- **Annual crops are not replanted.** They are removed when harvested; replant them yourself. Bushes that reset after harvest (e.g. berries) regrow by themselves.
- **One harvest can produce up to 2 stacks** (the crop plus Life Essence). When the storage is nearly full, whatever does not fit stays on the ground and is collected once space frees up.
- **Vacuums any dropped item** within radius + `CollectMargin` (2 by default), not only crops, unless `CollectAnyItem` is turned off (then tools, weapons and armor stay on the ground).
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
