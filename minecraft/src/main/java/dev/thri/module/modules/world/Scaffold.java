package dev.thri.module.modules.world;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.util.RotationUtil;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    private int savedSlot = -1;

    public Scaffold() { super("Scaffold", Category.WORLD, 0); }

    @Override protected void onDisable() {
        if (mc.player != null && savedSlot >= 0) mc.player.getInventory().selectedSlot = savedSlot;
        savedSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        BlockPos below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ());
        if (!mc.world.getBlockState(below).isAir()) return;

        int slot = selectBlock();
        if (slot < 0) return;
        if (savedSlot < 0) savedSlot = mc.player.getInventory().selectedSlot;
        mc.player.getInventory().selectedSlot = slot;

        BlockPos support = below.down();
        BlockState supState = mc.world.getBlockState(support);
        Direction face = Direction.UP;
        if (supState.isAir()) {
            // find any adjacent solid
            for (Direction d : Direction.values()) {
                if (!mc.world.getBlockState(below.offset(d)).isAir()) {
                    support = below.offset(d);
                    face = d.getOpposite();
                    break;
                }
            }
            if (mc.world.getBlockState(support).isAir()) return;
        }
        Vec3d hitVec = Vec3d.ofCenter(support).add(Vec3d.of(face.getVector()).multiply(0.5));
        float[] r = RotationUtil.rotationsTo(mc.player.getEyePos(), hitVec);
        mc.player.setYaw(r[0]); mc.player.setPitch(r[1]);
        BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int selectBlock() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.getItem() instanceof BlockItem bi && bi.getBlock().getDefaultState().isFullCube(mc.world, BlockPos.ORIGIN))
                return i;
        }
        return -1;
    }
}
