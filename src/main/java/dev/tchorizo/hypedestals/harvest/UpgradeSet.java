package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Upgrades installed in one machine ({@link UpgradeMachine}): at most one per type, only types the
 * machine accepts, kept in installation order so {@link #newest()} is the one to hand back first.
 * Persisted as the item ids of {@link #toArray()}.
 */
public final class UpgradeSet {

    private final UpgradeMachine machine;
    private final List<UpgradeType> installed = new ArrayList<>(UpgradeType.values().length);
    private final List<String> ignored;

    /** An empty Harvester set. */
    public UpgradeSet() {
        this(UpgradeMachine.HARVESTER);
    }

    public UpgradeSet(UpgradeMachine machine) {
        this(machine, List.of());
    }

    private UpgradeSet(UpgradeMachine machine, List<String> ignored) {
        this.machine = machine;
        this.ignored = ignored;
    }

    /** The machine this set belongs to. */
    public UpgradeMachine machine() {
        return machine;
    }

    /** Reads persisted ids of a Harvester, see {@link #parse(String[], UpgradeMachine)}. */
    public static UpgradeSet parse(String[] itemIds) {
        return parse(itemIds, UpgradeMachine.HARVESTER);
    }

    /**
     * Reads persisted ids in order. Unknown ids (e.g. from a newer version), upgrades the machine
     * does not accept and repeated types are dropped and reported by {@link #ignored()}; they
     * disappear on the next save.
     */
    public static UpgradeSet parse(String[] itemIds, UpgradeMachine machine) {
        if (itemIds == null || itemIds.length == 0) {
            return new UpgradeSet(machine);
        }
        List<String> skipped = new ArrayList<>(0);
        UpgradeSet set = new UpgradeSet(machine, skipped);
        for (String id : itemIds) {
            UpgradeType type = UpgradeType.fromItemId(id);
            if (type == null || !set.install(type)) {
                skipped.add(String.valueOf(id));
            }
        }
        return set;
    }

    /** Ids {@link #parse} skipped, in input order; empty for sets not built by {@code parse}. */
    public List<String> ignored() {
        return ignored;
    }

    public boolean has(UpgradeType type) {
        return installed.contains(type);
    }

    public boolean isEmpty() {
        return installed.isEmpty();
    }

    /** Whether the machine takes {@code type} at all (installed or not). */
    public boolean accepts(UpgradeType type) {
        return machine.accepts(type);
    }

    /**
     * Adds {@code type} as the newest upgrade; {@code false} (no change) when the machine does not
     * accept it ({@link #accepts}) or it is already installed.
     */
    public boolean install(UpgradeType type) {
        if (!machine.accepts(type) || installed.contains(type)) {
            return false;
        }
        installed.add(type);
        return true;
    }

    /** The newest upgrade, without removing it; {@code null} when none is installed. */
    public UpgradeType newest() {
        return installed.isEmpty() ? null : installed.getLast();
    }

    /** Removes {@code type}; {@code false} when it was not installed. */
    public boolean remove(UpgradeType type) {
        return installed.remove(type);
    }

    /** Removes every upgrade. */
    public void clear() {
        installed.clear();
    }

    /** Item ids in installation order; a fresh array each call. */
    public String[] toArray() {
        String[] ids = new String[installed.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = installed.get(i).itemId();
        }
        return ids;
    }

    /** Same machine, same upgrades in the same order; {@link #ignored()} is not carried over. */
    public UpgradeSet copy() {
        UpgradeSet copy = new UpgradeSet(machine);
        copy.installed.addAll(installed);
        return copy;
    }

    /** Display names in installation order, e.g. {@code [Watering, Fertilizer]}. */
    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (UpgradeType type : installed) {
            joiner.add(type.displayName());
        }
        return joiner.toString();
    }
}
