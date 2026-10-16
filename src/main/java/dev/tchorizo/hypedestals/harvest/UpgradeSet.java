package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Upgrades installed in one Harvester: at most one per type, kept in installation order so
 * {@link #removeLast()} hands back the newest. Persisted as the item ids of {@link #toArray()}.
 */
public final class UpgradeSet {

    private final List<UpgradeType> installed = new ArrayList<>(UpgradeType.values().length);
    private final List<String> ignored;

    public UpgradeSet() {
        this.ignored = List.of();
    }

    private UpgradeSet(List<String> ignored) {
        this.ignored = ignored;
    }

    /**
     * Reads persisted ids in order. Unknown ids (e.g. from a newer version) and repeated types are
     * dropped and reported by {@link #ignored()}; they disappear on the next save.
     */
    public static UpgradeSet parse(String[] itemIds) {
        if (itemIds == null || itemIds.length == 0) {
            return new UpgradeSet();
        }
        List<String> skipped = new ArrayList<>(0);
        UpgradeSet set = new UpgradeSet(skipped);
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

    /** Adds {@code type} as the newest upgrade; {@code false} (no change) when already installed. */
    public boolean install(UpgradeType type) {
        if (installed.contains(type)) {
            return false;
        }
        installed.add(type);
        return true;
    }

    /** Removes and returns the newest upgrade, or {@code null} when none is installed. */
    public UpgradeType removeLast() {
        return installed.isEmpty() ? null : installed.removeLast();
    }

    /** Removes {@code type}; {@code false} when it was not installed. */
    public boolean remove(UpgradeType type) {
        return installed.remove(type);
    }

    /** Item ids in installation order; a fresh array each call. */
    public String[] toArray() {
        String[] ids = new String[installed.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = installed.get(i).itemId();
        }
        return ids;
    }

    /** Same upgrades in the same order; {@link #ignored()} is not carried over. */
    public UpgradeSet copy() {
        UpgradeSet copy = new UpgradeSet();
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
