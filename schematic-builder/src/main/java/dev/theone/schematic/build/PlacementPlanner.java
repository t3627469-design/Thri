package dev.theone.schematic.build;

import dev.theone.schematic.util.Mc;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Works out how to click so that the game produces the wanted block state: which neighbour face to
 * click, where on that face, and which way the player must look.
 *
 * Instead of hard-coding the rules of every directional block, it asks the block itself: for each
 * candidate click and rotation it builds the same {@link ItemPlacementContext} the game would and
 * calls {@link Block#getPlacementState}. The first combination whose result matches the target's
 * placement properties wins. This covers stairs, logs, slabs, trapdoors, observers, pistons,
 * hoppers, wall torches, signs, buttons, anvils and every other block the same way.
 */
public final class PlacementPlanner {
    public record Plan(BlockHitResult hit, float yaw, float pitch, boolean needsRotation) {
    }

    /** Why planning failed; only {@link #NO_MATCH} means the block cannot be placed from here at all. */
    public enum Status {
        OK,
        /** No solid neighbour to click yet: wait for the build to grow towards it. */
        NO_SUPPORT,
        /** Neighbours exist but none of their faces is within reach: walk closer. */
        OUT_OF_RANGE,
        /** Reachable faces exist but no click/rotation gives the target state. */
        NO_MATCH
    }

    public record Result(Status status, Plan plan) {
    }

    /** True when some neighbour can be clicked to place a block at {@code pos}. */
    public static boolean hasSupport(MinecraftClient mc, BlockPos pos) {
        for (Direction side : DIRECTIONS) {
            if (isClickTarget(mc.world.getBlockState(Mc.offset(pos, side)))) return true;
        }
        return false;
    }

    private static boolean isClickTarget(BlockState s) {
        return !s.isAir() && !s.isReplaceable() && s.getFluidState().isEmpty() && !BlockUtils.isClickable(s.getBlock());
    }

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final float[] PITCHES = {0f, 89f, -89f};
    private static final float[] YAWS_4 = {0f, 90f, 180f, 270f};
    private static final float[] YAWS_16 = new float[16];

    static {
        for (int i = 0; i < 16; i++) YAWS_16[i] = i * 22.5f;
    }

    private PlacementPlanner() {
    }

    /**
     * @param target  state wanted at {@code pos}
     * @param stack   the item stack that will be used
     * @param range   max distance from the eyes to the click point
     * @param airPlace allow clicking the target position itself when it has no solid neighbour
     * @return the plan, or the reason there is none
     */
    public static Result plan(MinecraftClient mc, BlockPos pos, BlockState target, ItemStack stack, double range, boolean airPlace) {
        ClientPlayerEntity player = mc.player;
        Vec3d eye = player.getEyePos();
        boolean orientable = StateMatch.isOrientable(target);
        float[] yaws = !orientable ? null : hasRotation16(target) ? YAWS_16 : YAWS_4;

        float savedYaw = player.getYaw();
        float savedPitch = player.getPitch();
        boolean supported = false, inRange = false;
        try {
            for (Direction side : DIRECTIONS) {
                BlockPos neighbor = Mc.offset(pos, side);
                if (!isClickTarget(mc.world.getBlockState(neighbor))) continue;
                supported = true;

                Direction face = side.getOpposite();
                for (double hy : heights(side)) {
                    Vec3d hitVec = new Vec3d(
                        pos.getX() + 0.5 + side.getOffsetX() * 0.5,
                        pos.getY() + hy,
                        pos.getZ() + 0.5 + side.getOffsetZ() * 0.5);
                    if (eye.squaredDistanceTo(hitVec) > range * range) continue;
                    inRange = true;
                    BlockHitResult hit = new BlockHitResult(hitVec, face, neighbor, false);
                    Plan p = tryRotations(player, pos, target, stack, hit, hitVec, yaws, orientable);
                    if (p != null) return new Result(Status.OK, p);
                }
            }
            if (airPlace && !supported) {
                supported = true;
                Vec3d center = Vec3d.ofCenter(pos);
                if (eye.squaredDistanceTo(center) <= range * range) {
                    inRange = true;
                    BlockHitResult hit = new BlockHitResult(center, Mc.UP, pos, false);
                    Plan p = tryRotations(player, pos, target, stack, hit, center, yaws, orientable);
                    if (p != null) return new Result(Status.OK, p);
                }
            }
        } finally {
            player.setYaw(savedYaw);
            player.setPitch(savedPitch);
        }
        Status status = !supported ? Status.NO_SUPPORT : !inRange ? Status.OUT_OF_RANGE : Status.NO_MATCH;
        return new Result(status, null);
    }

    private static Plan tryRotations(ClientPlayerEntity player, BlockPos pos, BlockState target, ItemStack stack,
                                     BlockHitResult hit, Vec3d hitVec, float[] yaws, boolean orientable) {
        float lookYaw = (float) yawTo(player.getEyePos(), hitVec);
        float lookPitch = (float) pitchTo(player.getEyePos(), hitVec);
        if (!orientable) {
            player.setYaw(lookYaw);
            player.setPitch(lookPitch);
            BlockState result = predict(player, target, stack, hit, pos);
            if (result == null || result.getBlock() != target.getBlock()) return null;
            return new Plan(hit, lookYaw, lookPitch, false);
        }
        // Try looking straight at the click point first: most blocks get the right state that way
        // and it is the most natural rotation to send.
        player.setYaw(lookYaw);
        player.setPitch(lookPitch);
        if (predictsTarget(player, target, stack, hit, pos)) {
            return new Plan(hit, lookYaw, lookPitch, true);
        }
        for (float pitch : PITCHES) {
            for (float yaw : yaws) {
                player.setYaw(yaw);
                player.setPitch(pitch);
                if (predictsTarget(player, target, stack, hit, pos)) {
                    return new Plan(hit, yaw, pitch, true);
                }
            }
        }
        return null;
    }

    private static boolean predictsTarget(ClientPlayerEntity player, BlockState target, ItemStack stack, BlockHitResult hit, BlockPos pos) {
        BlockState predicted = predict(player, target, stack, hit, pos);
        return predicted != null && StateMatch.matches(predicted, target);
    }

    private static BlockState predict(ClientPlayerEntity player, BlockState target, ItemStack stack, BlockHitResult hit, BlockPos pos) {
        try {
            ItemPlacementContext ctx = new ItemPlacementContext(player, Mc.MAIN_HAND, stack, hit);
            if (!ctx.getBlockPos().equals(pos)) return null;
            if (!ctx.canPlace()) return null;
            return target.getBlock().getPlacementState(ctx);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Click heights on a face: lower and upper half for side faces (slab/stair half), fixed for top/bottom. */
    private static double[] heights(Direction side) {
        if (side == Mc.DOWN) return new double[]{0.0};
        if (side == Mc.UP) return new double[]{1.0};
        return new double[]{0.25, 0.75};
    }

    private static boolean hasRotation16(BlockState state) {
        for (var p : state.getProperties()) if (p.getName().equals("rotation")) return true;
        return false;
    }

    public static double yawTo(Vec3d from, Vec3d to) {
        return Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0;
    }

    public static double pitchTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }
}
