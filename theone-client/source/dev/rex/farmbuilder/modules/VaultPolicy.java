package dev.rex.farmbuilder.modules;

import java.util.Set;

/** Decides which items are valuable enough to lock in the outside chest so a death cannot take them. */
final class VaultPolicy {
    private static final Set<String> VALUABLE = Set.of(
        "diamond", "diamond_block", "netherite_ingot", "netherite_scrap", "netherite_block", "ancient_debris",
        "emerald", "emerald_block", "enchanted_golden_apple", "elytra", "nether_star",
        "beacon", "enchanted_book", "trident", "heart_of_the_sea", "dragon_egg", "echo_shard", "wither_skeleton_skull",
        "netherite_upgrade_smithing_template");

    private VaultPolicy() {}

    /** @param path registry path without namespace, e.g. "diamond" or "red_shulker_box" */
    static boolean isValuable(String path) {
        if (path == null) return false;
        return VALUABLE.contains(path) || path.equals("shulker_box") || path.endsWith("_shulker_box");
    }
}
