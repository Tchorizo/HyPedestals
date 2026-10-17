package dev.tchorizo.hypedestals.harvest;

/** A quantity of one item id (e.g. the most a harvest can drop of it). */
public record ItemCount(String itemId, int quantity) {}
