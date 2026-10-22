package dev.tchorizo.hypedestals.harvest;

import java.util.EnumSet;
import java.util.Set;

/**
 * The machines that take upgrades, each with the upgrades it accepts. The install and remove
 * gestures, the drop on breaking and the persisted list are the same for all of them
 * ({@link UpgradeSet}); only the accepted set differs. Pure.
 */
public enum UpgradeMachine {
    /** Takes every upgrade. */
    HARVESTER("Harvester", EnumSet.allOf(UpgradeType.class)),
    /** Takes the Planter only: it plants saplings from its container on its own grid. */
    LUMBERJACK("Lumberjack", EnumSet.of(UpgradeType.PLANTER));

    private final String displayName;
    private final Set<UpgradeType> accepted;

    UpgradeMachine(String displayName, Set<UpgradeType> accepted) {
        this.displayName = displayName;
        this.accepted = accepted;
    }

    public String displayName() {
        return displayName;
    }

    public boolean accepts(UpgradeType type) {
        return accepted.contains(type);
    }
}
