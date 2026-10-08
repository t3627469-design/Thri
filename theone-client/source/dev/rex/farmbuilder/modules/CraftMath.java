package dev.rex.farmbuilder.modules;

/** Batch sizing for crafting and smelting, kept free of Minecraft classes so it can be tested headlessly. */
final class CraftMath {
    static final int SMELT_BATCH = 64;
    static final int INGREDIENT_CAP = 128;

    private CraftMath() {}

    /** How many crafts to do in one go: enough for what is missing, at most one stack of output. */
    static int batchCrafts(int missing, int outputPerCraft) {
        int out = Math.max(1, outputPerCraft);
        int wanted = (Math.max(1, missing) + out - 1) / out;
        return Math.max(1, Math.min(wanted, Math.max(1, 64 / out)));
    }

    /** Items of one ingredient to hold before crafting {@code crafts} times, capped so one trip stays reasonable. */
    static int ingredientTarget(int perCraft, int crafts) {
        return Math.min(perCraft * crafts, Math.max(perCraft, INGREDIENT_CAP));
    }

    /** How many crafts the first matching stacks can feed; placing from a stack that is too small would stall the grid. */
    static int feasibleCrafts(int wanted, int[] stackSizes, int[] perCraft) {
        int n = Math.max(1, wanted);
        for (int i = 0; i < stackSizes.length; i++) n = Math.min(n, Math.max(1, stackSizes[i] / Math.max(1, perCraft[i])));
        return n;
    }
}
