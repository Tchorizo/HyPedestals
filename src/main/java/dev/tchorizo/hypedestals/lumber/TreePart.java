package dev.tchorizo.hypedestals.lumber;

/** What a block is to the Lumberjack: part of a tree's wood, its leaves, or neither. */
public enum TreePart {
    NONE,
    /** Trunk, branch or root (the vanilla {@code TreeWood} list). */
    WOOD,
    /** Leaves (the vanilla {@code TreeLeaves} list or the tag {@code Family=Leaves}). */
    LEAVES
}
