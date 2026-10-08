package dev.rex.farmbuilder.modules;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_7923;

final class FarmLogic {
    static final Set<String> IGNORED_PROPERTIES = Set.of("powered", "power", "lit", "triggered", "enabled", "extended",
        "open", "in_wall", "waterlogged", "distance", "persistent", "age", "short", "locked", "delay", "north", "south",
        "east", "west", "up", "down", "shape", "level", "drag", "crafting", "occupied", "attached", "disarmed",
        "moisture", "stage", "note", "instrument", "has_book", "has_record", "berries");
    private static final Pattern PRICE = Pattern.compile("(?<![0-9.,-])\\$?\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)(?![0-9.,])\\s*(?:([kKmMbBtT])(?![A-Za-z]))?");

   static void configureBuilderSettings(baritone.api.Settings s) {
      s.buildInLayers.value = true;
      s.layerHeight.value = 1;
      s.startAtLayer.value = 0;
      s.skipFailedLayers.value = false;
      s.buildOnlySelection.value = false;
      s.buildIgnoreDirection.value = false;
      s.buildIgnoreBlocks.value = new java.util.ArrayList<>();
      s.buildRepeatCount.value = 1;
      s.buildRepeat.value = new net.minecraft.class_2382(0, 0, 0);
   }

    static net.minecraft.class_2338 junkCorner(net.minecraft.class_2338 origin, int dx, int dz) {
        return new net.minecraft.class_2338(origin.method_10263() - 8 - Math.max(0, Math.min(2, dx)),
            origin.method_10264(), origin.method_10260() - 8 - Math.max(0, Math.min(2, dz)));
    }

    enum BuildRetry { RESUME, RESTART, STOP }

    static BuildRetry buildRetry(int attempt) {
        if (attempt > 6) return BuildRetry.STOP;
        return attempt % 3 == 0 ? BuildRetry.RESTART : BuildRetry.RESUME;
    }

    static boolean matches(class_2680 have, class_2680 want) {
        if (have.method_26204() != want.method_26204()) return false;
        Map<class_2769<?>, Comparable<?>> existing = have.method_11656();
        for (Map.Entry<class_2769<?>, Comparable<?>> property : want.method_11656().entrySet()) {
            if (!IGNORED_PROPERTIES.contains(property.getKey().method_11899())
                && !Objects.equals(existing.get(property.getKey()), property.getValue())) return false;
        }
        return true;
    }

    static double parsePrice(String text) {
        if (text == null) return -1.0;
        text = text.replaceAll("\u00a7[0-9A-FK-ORa-fk-or]", "").trim();
        if (text.matches(".*-\\s*\\$?\\s*[0-9].*")) return -1.0;
        Matcher match = PRICE.matcher(text);
        if (!match.find()) return -1.0;
        double value;
        try { value = Double.parseDouble(match.group(1).replace(",", "")); }
        catch (NumberFormatException e) { return -1.0; }
        if (match.group(2) != null) value *= switch (Character.toLowerCase(match.group(2).charAt(0))) {
            case 'k' -> 1000.0;
            case 'm' -> 1000000.0;
            case 'b' -> 1000000000.0;
            case 't' -> 1000000000000.0;
            default -> 1.0;
        };
        return Double.isFinite(value) && value > 0.0 ? value : -1.0;
    }

    static void configureMiningSettings(baritone.api.Settings settings, int ceiling) {
        settings.allowBreak.value = true;
        settings.allowPlace.value = true;
        settings.allowInventory.value = true;
        settings.autoTool.value = true;
        settings.forceInternalMining.value = true;
        settings.assumeExternalAutoTool.value = false;
        settings.allowOnlyExposedOres.value = false;
        settings.minYLevelWhileMining.value = 0;
        settings.maxYLevelWhileMining.value = ceiling;
        settings.blacklistClosestOnFailure.value = true;
        settings.exploreForBlocks.value = true;
    }

    static boolean hasStackRoom(class_1799 stack) {
        return stack != null && !stack.method_7960() && stack.method_7947() < stack.method_7914();
    }

    static boolean nearFarm(net.minecraft.class_2338 feet, net.minecraft.class_2338 origin, int width, int height, int length) {
        if (feet == null || origin == null || width <= 0 || height <= 0 || length <= 0) return false;
        long dx = (long) feet.method_10263() - origin.method_10263();
        long dy = (long) feet.method_10264() - origin.method_10264();
        long dz = (long) feet.method_10260() - origin.method_10260();
        return dx >= -8 && dx < (long) width + 8 && dy >= -8 && dy < (long) height + 8
            && dz >= -8 && dz < (long) length + 8;
    }

    static java.util.List<net.minecraft.class_2248> travelProtection(java.util.List<net.minecraft.class_2248> blocks) {
        Set<String> terrain = Set.of("stone", "cobblestone", "deepslate", "cobbled_deepslate", "tuff", "granite", "diorite", "andesite",
            "dirt", "grass_block", "coarse_dirt", "rooted_dirt", "mud", "clay", "sand", "red_sand", "gravel", "netherrack", "end_stone");
        return new java.util.ArrayList<>(blocks.stream().filter(block -> !terrain.contains(class_7923.field_41175.method_10221(block).method_12832())).toList());
    }

    static boolean breakingProgress(int elapsed) { return elapsed >= 0 && elapsed < 600; }

    private static final String[] NATURAL = {"grass", "dirt", "podzol", "mycelium", "mud", "clay", "sand", "gravel", "stone",
        "deepslate", "tuff", "granite", "diorite", "andesite", "calcite", "dripstone", "_log", "leaves", "sapling", "flower",
        "fern", "bush", "vine", "lily", "mushroom", "moss", "snow", "ice", "cactus", "sugar_cane", "kelp", "seagrass", "pumpkin",
        "melon", "dandelion", "poppy", "tulip", "orchid", "allium", "bluet", "daisy", "cornflower", "rose", "lilac", "peony",
        "sunflower", "azalea", "roots", "propagule", "bamboo", "cobweb", "netherrack", "_ore", "lichen", "petals", "pitcher",
        "torchflower", "dead_bush", "hanging_moss", "eyeblossom", "leaf_litter", "cactus_flower", "firefly_bush", "terracotta"};
    private static final String[] MADE = {"brick", "slab", "stairs", "wall", "planks", "fence", "door", "glass", "wool",
        "carpet", "concrete", "polished", "chiseled", "cut_", "smooth_", "button", "pressure_plate", "sign", "bed", "torch",
        "lantern", "rail", "pane", "bars", "stripped", "_wood", "block_of", "_block", "tiles", "glazed"};

    /** True for terrain and plants that are safe to clear; false for anything that looks player-made. */
    static boolean naturalBlock(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.equals("grass_block") || path.equals("snow_block") || path.equals("moss_block") || path.equals("mud")) return true;
        for (String m : MADE) if (path.contains(m)) return false;
        for (String n : NATURAL) if (path.contains(n)) return true;
        return false;
    }

    static boolean preferHome(boolean gathering, boolean quick, boolean homeSet, double distance, int minimum) {
        return gathering || quick && homeSet && Double.isFinite(distance) && distance >= minimum;
    }

    static boolean homeAttemptDue(boolean gathering, boolean required, int attempts, int tick, int next) {
        return attempts == 0 || (gathering || required) && attempts < 3 && tick >= next;
    }

    static boolean worthwhilePickup(boolean needed, double distance, double currentGoalCost, double itemGoalCost) {
        if (!Double.isFinite(distance) || distance <= 1.5 || distance >= 10) return false;
        if (needed) return true;
        return distance <= 3 && Double.isFinite(currentGoalCost) && Double.isFinite(itemGoalCost)
            && itemGoalCost + distance * 3.563 <= currentGoalCost + 2.0;
    }

    static boolean failedJump(boolean grounded, boolean airborneSeen, boolean belowStart, int elapsed, boolean reached) {
        if (reached || !grounded) return false;
        return airborneSeen && (belowStart || elapsed >= 20) || elapsed >= 60;
    }

    record DigTool(int slot, float speed, boolean suitable, boolean usable) {}

    static int bestDigTool(java.util.List<DigTool> tools, boolean requiresTool, int current) {
        DigTool best = null;
        for (DigTool tool : tools) {
            if (!tool.usable() || !Float.isFinite(tool.speed()) || tool.speed() <= 1
                || (requiresTool && !tool.suitable())) continue;
            if (best == null || tool.speed() > best.speed()
                || (tool.speed() == best.speed() && tool.slot() == current)) best = tool;
        }
        return best == null ? -1 : best.slot();
    }

    static int bestDigTool(class_1799[] inventory, class_2680 block, int current) {
        java.util.List<DigTool> tools = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.length; i++) {
            class_1799 stack = inventory[i];
            if (stack == null || !usableGear(stack)) continue;
            tools.add(new DigTool(i, stack.method_7924(block), stack.method_7951(block), true));
        }
        return bestDigTool(tools, block.method_29291(), current);
    }

    static boolean usableGear(class_1799 stack) {
        return stack != null && !stack.method_7960() && (!stack.method_7963()
            || stack.method_7936() - stack.method_7919() > Math.max(10, stack.method_7936() * 0.08));
    }

    static boolean preferArmor(class_1799 candidate, class_1799 worn) {
        if (!usableGear(candidate)) return false;
        if (!usableGear(worn)) return true;
        int candidateTier = toolTier(candidate.method_7909());
        int wornTier = toolTier(worn.method_7909());
        if (candidateTier != wornTier) return candidateTier > wornTier;
        return false;
    }

    static String toolFamily(class_1792 item) {
        String name = class_7923.field_41178.method_10221(item).method_12832();
        if (name.endsWith("_pickaxe")) return "pickaxe";
        if (name.endsWith("_axe")) return "axe";
        if (name.endsWith("_shovel")) return "shovel";
        return null;
    }

    static int toolTier(class_1792 item) {
        String name = class_7923.field_41178.method_10221(item).method_12832();
        if (name.startsWith("netherite_")) return 5;
        if (name.startsWith("diamond_")) return 4;
        if (name.startsWith("iron_")) return 3;
        if (name.startsWith("stone_")) return 2;
        if (name.startsWith("wooden_")) return 1;
        return 0;
    }

    static boolean usableTool(class_1799 stack, String family, int percent) {
        if (stack.method_7960() || !family.equals(toolFamily(stack.method_7909()))) return false;
        int maximum = stack.method_7936();
        int minimum = Math.max(10, (int) Math.ceil(maximum * Math.max(8, percent) / 100.0));
        return maximum <= 0 || maximum - stack.method_7919() > minimum;
    }
}
