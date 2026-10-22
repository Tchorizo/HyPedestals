package dev.tchorizo.hypedestals.harvest;

import java.util.ArrayList;
import java.util.List;

/**
 * What the upgrade indicator shows when a machine's storage is opened: every upgrade the machine
 * accepts, in {@link UpgradeType} declaration order (not installation order, so the line does not
 * jump around), each marked installed or missing. Missing ones are listed on purpose: they teach
 * that the upgrades exist. Pure; the client text is built from this in {@code block/}.
 */
public final class UpgradeIndicator {

    /** One line of the indicator: an upgrade the machine accepts and whether it is installed. */
    public record Entry(UpgradeType type, boolean installed) {}

    private UpgradeIndicator() {}

    /** Accepted upgrades of {@code set}'s machine, in declaration order, each marked installed or missing. */
    public static List<Entry> entries(UpgradeSet set) {
        List<Entry> entries = new ArrayList<>(UpgradeType.values().length);
        for (UpgradeType type : UpgradeType.values()) {
            if (set.accepts(type)) {
                entries.add(new Entry(type, set.has(type)));
            }
        }
        return entries;
    }
}
