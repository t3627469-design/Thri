package dev.theone.schematic.build;

import dev.theone.schematic.util.Mc;
import dev.theone.schematic.format.Schematic;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A schematic resolved against the game registries and placed in the world: every block that the
 * builder has to produce, in world coordinates, already rotated and mirrored.
 */
public final class BuildTarget {
    /** How a target block gets produced. */
    public enum Kind {
        /** Place the block's item. */
        PLACE,
        /** Place dirt, then till it with a hoe. */
        FARMLAND,
        /** Empty a water or lava bucket. */
        FLUID
    }

    public static final class Block3 {
        public final BlockPos pos;
        public final long key;
        public final BlockState state;
        public final Item item;
        public final Kind kind;

        Block3(BlockPos pos, BlockState state, Item item, Kind kind) {
            this.pos = pos;
            this.key = pos.asLong();
            this.state = state;
            this.item = item;
            this.kind = kind;
        }
    }

    public final String name;
    /** Blocks in build order: bottom layer first. */
    public final List<Block3> blocks;
    public final Map<Long, Block3> byPos;
    /** Block ids present in the file that this game version does not know. */
    public final Map<String, Integer> unknown;
    /** Blocks the builder cannot produce at all (no item, e.g. fire or piston heads). */
    public final Map<String, Integer> skipped;
    public final BlockPos min, max;

    private BuildTarget(String name, List<Block3> blocks, Map<String, Integer> unknown, Map<String, Integer> skipped) {
        this.name = name;
        this.blocks = blocks;
        this.unknown = unknown;
        this.skipped = skipped;
        this.byPos = new LinkedHashMap<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Block3 b : blocks) {
            byPos.put(b.key, b);
            minX = Math.min(minX, b.pos.getX()); minY = Math.min(minY, b.pos.getY()); minZ = Math.min(minZ, b.pos.getZ());
            maxX = Math.max(maxX, b.pos.getX()); maxY = Math.max(maxY, b.pos.getY()); maxZ = Math.max(maxZ, b.pos.getZ());
        }
        this.min = blocks.isEmpty() ? BlockPos.ORIGIN : new BlockPos(minX, minY, minZ);
        this.max = blocks.isEmpty() ? BlockPos.ORIGIN : new BlockPos(maxX, maxY, maxZ);
    }

    public static BuildTarget create(Schematic schematic, BlockPos origin, BlockRotation rotation, BlockMirror mirror) {
        List<Block3> out = new ArrayList<>(schematic.blocks.size());
        Map<String, Integer> unknown = new LinkedHashMap<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        Map<Schematic.StateSpec, BlockState> cache = new LinkedHashMap<>();

        for (Schematic.Entry e : schematic.blocks) {
            BlockState state = cache.computeIfAbsent(e.state(), BuildTarget::resolve);
            if (state == null) {
                unknown.merge(e.state().id(), 1, Integer::sum);
                continue;
            }
            if (state.isAir() || isSecondaryPart(e.state())) continue;

            // Mirror first, then rotate, the same order vanilla structure templates use.
            int x = e.x(), z = e.z();
            int sx = schematic.sizeX, sz = schematic.sizeZ;
            if (mirror == Mc.MIRROR_FRONT_BACK) x = sx - 1 - x;
            else if (mirror == Mc.MIRROR_LEFT_RIGHT) z = sz - 1 - z;
            int rx, rz;
            if (rotation == Mc.ROT_CW_90) {
                rx = sz - 1 - z; rz = x;
            } else if (rotation == Mc.ROT_180) {
                rx = sx - 1 - x; rz = sz - 1 - z;
            } else if (rotation == Mc.ROT_CCW_90) {
                rx = z; rz = sx - 1 - x;
            } else {
                rx = x; rz = z;
            }
            BlockState placed = state.mirror(mirror).rotate(rotation);

            Kind kind;
            Item item;
            Block block = placed.getBlock();
            if (block == Mc.WATER || block == Mc.LAVA) {
                // Only sources can be placed; flowing fluid fills itself in.
                if (!"0".equals(e.state().props().getOrDefault("level", "0"))) continue;
                kind = Kind.FLUID;
                item = block == Mc.WATER ? Mc.WATER_BUCKET : Mc.LAVA_BUCKET;
            } else if (block == Mc.FARMLAND) {
                kind = Kind.FARMLAND;
                item = Mc.DIRT_ITEM;
            } else {
                kind = Kind.PLACE;
                item = block.asItem();
                if (item == Mc.ITEM_AIR) {
                    skipped.merge(e.state().id(), 1, Integer::sum);
                    continue;
                }
            }
            BlockPos pos = new BlockPos(origin.getX() + rx, origin.getY() + e.y(), origin.getZ() + rz);
            out.add(new Block3(pos, placed, item, kind));
        }

        // Bottom-up, then a stable sweep across each layer. Fluids last so water does not flood
        // positions that still need blocks.
        out.sort(Comparator
            .comparingInt((Block3 b) -> b.kind == Kind.FLUID ? 1 : 0)
            .thenComparingInt(b -> b.pos.getY())
            .thenComparingInt(b -> b.pos.getX())
            .thenComparingInt(b -> b.pos.getZ()));
        return new BuildTarget(schematic.name, out, unknown, skipped);
    }

    /** Upper door/plant halves, bed heads and the like appear on their own once the base is placed. */
    private static boolean isSecondaryPart(Schematic.StateSpec spec) {
        String half = spec.props().get("half");
        if ("upper".equals(half)) return true;
        return "head".equals(spec.props().get("part"));
    }

    static BlockState resolve(Schematic.StateSpec spec) {
        Identifier id = Identifier.tryParse(spec.id());
        if (id == null || !Registries.BLOCK.containsId(id)) return null;
        Block block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();
        for (Map.Entry<String, String> p : spec.props().entrySet()) {
            Property<?> property = block.getStateManager().getProperty(p.getKey());
            if (property == null) continue;
            state = withParsed(state, property, p.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withParsed(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.parse(value);
        return parsed.isPresent() ? state.with(property, parsed.get()) : state;
    }
}
