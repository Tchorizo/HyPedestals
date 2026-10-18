package dev.tchorizo.hypedestals.harvest;

/** Pure classification of farming blocks the Harvester may pass to the vanilla harvest operation. */
public final class HarvestTargetRule {

    private HarvestTargetRule() {}

    /**
     * Farming metadata alone does not make a block a crop. Soil has no growth stages, while
     * saplings, cacti and mushroom spreaders have stages that replace or spread the original
     * block. Harvesting either kind would prevent its intended growth.
     */
    public static boolean isTarget(boolean hasFarming, boolean hasGrowthStages, boolean hasPrefabOrSpreadStage) {
        return hasFarming && hasGrowthStages && !hasPrefabOrSpreadStage;
    }

    /** A block with growth stages can receive soil care even when it must not be harvested. */
    public static boolean isGrowing(boolean hasFarming, boolean hasGrowthStages) {
        return hasFarming && hasGrowthStages;
    }
}
