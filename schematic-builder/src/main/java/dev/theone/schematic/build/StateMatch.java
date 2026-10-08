package dev.theone.schematic.build;

import dev.theone.schematic.util.Mc;
import net.minecraft.block.BlockState;
import net.minecraft.state.property.Property;

import java.util.Set;

/**
 * Decides whether a block in the world counts as "built" for a target state. Only properties the
 * player controls when placing are compared; neighbour-driven ones (fence connections, stair
 * shape, redstone power, leaf distance, waterlogging...) are left to the game.
 */
public final class StateMatch {
    /** Properties decided by how the block is placed. */
    private static final Set<String> PLACEMENT_PROPS = Set.of(
        "facing", "axis", "half", "face", "hinge", "rotation", "hanging", "attachment",
        "orientation", "vertical_direction");

    private StateMatch() {
    }

    public static boolean matches(BlockState world, BlockState target) {
        if (world.getBlock() != target.getBlock()) return false;
        if (target.getBlock() == Mc.WATER || target.getBlock() == Mc.LAVA) {
            return world.getFluidState().isStill();
        }
        for (Property<?> p : target.getProperties()) {
            if (!isPlacementProperty(p, target)) continue;
            if (!world.contains(p) || !world.get(p).equals(target.get(p))) return false;
        }
        return true;
    }

    /** True when the target has any property whose value depends on where/how it is placed. */
    public static boolean isOrientable(BlockState target) {
        for (Property<?> p : target.getProperties()) if (isPlacementProperty(p, target)) return true;
        return false;
    }

    static boolean isPlacementProperty(Property<?> p, BlockState state) {
        String name = p.getName();
        if (PLACEMENT_PROPS.contains(name)) return true;
        // "type" is the slab half (top/bottom/double); on chests it is the neighbour connection.
        if (name.equals("type")) {
            String v = valueName(state, p);
            return v.equals("top") || v.equals("bottom") || v.equals("double");
        }
        return false;
    }

    public static <T extends Comparable<T>> String valueName(BlockState state, Property<T> p) {
        return p.name(state.get(p));
    }

    public static boolean isDoubleSlab(BlockState state) {
        return "double".equals(slabType(state));
    }

    /** The value of a slab's "type" property (top/bottom/double), or null for anything else. */
    public static String slabType(BlockState state) {
        for (Property<?> p : state.getProperties()) {
            if (!p.getName().equals("type")) continue;
            String v = valueName(state, p);
            return v.equals("top") || v.equals("bottom") || v.equals("double") ? v : null;
        }
        return null;
    }

    /** The single-slab version of a double slab target (bottom half), used for the first placement. */
    public static BlockState singleSlab(BlockState doubleSlab) {
        for (Property<?> p : doubleSlab.getProperties()) {
            if (p.getName().equals("type")) return withNamed(doubleSlab, p, "bottom");
        }
        return doubleSlab;
    }

    private static <T extends Comparable<T>> BlockState withNamed(BlockState state, Property<T> p, String value) {
        return p.parse(value).map(v -> state.with(p, v)).orElse(state);
    }
}
