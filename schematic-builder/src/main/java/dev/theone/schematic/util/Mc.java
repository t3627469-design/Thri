package dev.theone.schematic.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Game constants looked up at runtime instead of through static fields.
 *
 * Registry constants (Blocks.DIRT, Items.WATER_BUCKET...) and enum constants are named by yarn's
 * build from the game jar, so their intermediary names are not part of the yarn source mappings
 * this addon is checked against. Resolving them by registry id, or by what the constant does,
 * keeps every reference verifiable and independent of field order.
 */
public final class Mc {
    public static final Direction DOWN = direction(0, -1, 0);
    public static final Direction UP = direction(0, 1, 0);
    public static final Direction NORTH = direction(0, 0, -1);
    public static final Direction SOUTH = direction(0, 0, 1);
    public static final Direction WEST = direction(-1, 0, 0);
    public static final Direction EAST = direction(1, 0, 0);

    /** Hand declares MAIN_HAND then OFF_HAND, unchanged since 1.9. */
    public static final Hand MAIN_HAND = Hand.values()[0];
    public static final Hand OFF_HAND = Hand.values()[1];

    public static final BlockRotation ROT_NONE = rotation(NORTH);
    public static final BlockRotation ROT_CW_90 = rotation(EAST);
    public static final BlockRotation ROT_180 = rotation(SOUTH);
    public static final BlockRotation ROT_CCW_90 = rotation(WEST);

    public static final BlockMirror MIRROR_NONE = mirror(NORTH, NORTH, EAST, EAST);
    /** Flips north/south (the z axis). */
    public static final BlockMirror MIRROR_LEFT_RIGHT = mirror(NORTH, SOUTH, EAST, EAST);
    /** Flips east/west (the x axis). */
    public static final BlockMirror MIRROR_FRONT_BACK = mirror(NORTH, NORTH, EAST, WEST);

    public static final Block AIR = block("air");
    public static final Block WATER = block("water");
    public static final Block LAVA = block("lava");
    public static final Block FARMLAND = block("farmland");
    public static final Block DIRT = block("dirt");
    public static final Block GRASS_BLOCK = block("grass_block");
    public static final Block DIRT_PATH = block("dirt_path");

    public static final Item ITEM_AIR = item("air");
    public static final Item DIRT_ITEM = item("dirt");
    public static final Item WATER_BUCKET = item("water_bucket");
    public static final Item LAVA_BUCKET = item("lava_bucket");
    public static final Item WOODEN_HOE = item("wooden_hoe");
    public static final Item COBBLESTONE = item("cobblestone");
    public static final Item NETHER_STAR = item("nether_star");

    private Mc() {
    }

    public static Block block(String path) {
        return Registries.BLOCK.get(Identifier.tryParse("minecraft:" + path));
    }

    public static Item item(String path) {
        return Registries.ITEM.get(Identifier.tryParse("minecraft:" + path));
    }

    /**
     * Neighbour position. BlockPos.offset/down are covariant overrides of Vec3i methods and have
     * their own intermediary names, so positions are built from coordinates instead.
     */
    public static BlockPos offset(BlockPos pos, Direction d) {
        return new BlockPos(pos.getX() + d.getOffsetX(), pos.getY() + d.getOffsetY(), pos.getZ() + d.getOffsetZ());
    }

    public static BlockPos below(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY() - 1, pos.getZ());
    }

    public static BlockPos above(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY() + 1, pos.getZ());
    }

    private static Direction direction(int x, int y, int z) {
        for (Direction d : Direction.values()) {
            if (d.getOffsetX() == x && d.getOffsetY() == y && d.getOffsetZ() == z) return d;
        }
        throw new IllegalStateException("no direction " + x + "," + y + "," + z);
    }

    private static BlockRotation rotation(Direction northBecomes) {
        for (BlockRotation r : BlockRotation.values()) {
            if (r.rotate(NORTH) == northBecomes) return r;
        }
        throw new IllegalStateException("no rotation turning north into " + northBecomes);
    }

    private static BlockMirror mirror(Direction north, Direction northBecomes, Direction east, Direction eastBecomes) {
        for (BlockMirror m : BlockMirror.values()) {
            if (m.apply(north) == northBecomes && m.apply(east) == eastBecomes) return m;
        }
        throw new IllegalStateException("no matching mirror");
    }
}
