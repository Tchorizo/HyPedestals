package dev.tchorizo.hypedestals.lumber;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SaplingChoiceTest {

    @Test
    void speciesFromTheWoodId() {
        assertEquals("Plant_Sapling_Oak", SaplingChoice.saplingOf("Wood_Oak_Trunk"));
        assertEquals("Plant_Sapling_Oak", SaplingChoice.saplingOf("Wood_Oak_Trunk_Full"));
        assertEquals("Plant_Sapling_Birch", SaplingChoice.saplingOf("Wood_Birch_Branch_Long"));
        assertEquals("Plant_Sapling_Fig_Blue", SaplingChoice.saplingOf("Wood_Fig_Blue_Roots"));
        assertEquals("Plant_Sapling_Wisteria_Wild", SaplingChoice.saplingOf("Wood_Wisteria_Wild_Roots"));
        assertNull(SaplingChoice.saplingOf("Plant_Leaves_Oak"));
        assertNull(SaplingChoice.saplingOf(null));
    }

    @Test
    void sameSpeciesFirstThenTheFirstSlot() {
        List<String> saplings = List.of("Plant_Sapling_Birch", "Plant_Sapling_Oak");

        assertEquals("Plant_Sapling_Oak", SaplingChoice.pick("Wood_Oak_Trunk", saplings));
        assertEquals("Plant_Sapling_Birch", SaplingChoice.pick("Wood_Fir_Trunk", saplings));
        assertEquals("Plant_Sapling_Birch", SaplingChoice.pick(null, saplings));
        assertNull(SaplingChoice.pick("Wood_Oak_Trunk", List.of()));
    }
}
