package dev.tchorizo.hypedestals.harvest;

/** Upgrades a Harvester can hold, each backed by one item id. */
public enum UpgradeType {
    WATERING("HyPedestals_Upgrade_Watering", "Watering"),
    FERTILIZER("HyPedestals_Upgrade_Fertilizer", "Fertilizer");

    private final String itemId;
    private final String displayName;

    UpgradeType(String itemId, String displayName) {
        this.itemId = itemId;
        this.displayName = displayName;
    }

    /** Id of the item that installs this upgrade; also what the Harvester persists. */
    public String itemId() {
        return itemId;
    }

    public String displayName() {
        return displayName;
    }

    /** The upgrade installed by {@code itemId}, or {@code null} when that item is not an upgrade. */
    public static UpgradeType fromItemId(String itemId) {
        for (UpgradeType type : values()) {
            if (type.itemId.equals(itemId)) {
                return type;
            }
        }
        return null;
    }
}
