package io.github.bl4z3es.riseofempires.core.model;

/**
 * One block of a blueprint, relative to the blueprint's origin before rotation.
 *
 * @param blockState the block as Minecraft writes it, e.g. {@code minecraft:oak_stairs[facing=north]}
 * @param item       the item a builder takes from the stockpile to place it, or "" if it costs nothing
 */
public record BlueprintBlock(int dx, int dy, int dz, String blockState, String item) {}
