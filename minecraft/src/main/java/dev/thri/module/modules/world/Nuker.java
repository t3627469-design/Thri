package dev.thri.module.modules.world;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class Nuker extends Module {
    public final Setting<Double> range = add(new Setting<>("Range", 4.5, 1.0, 6.0));
    public Nuker() { super("Nuker", Category.WORLD, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        BlockPos center = mc.player.getBlockPos();
        int r = (int) Math.ceil(range.get());
        BlockPos best = null; double bestDist = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            BlockPos p = center.add(dx, dy, dz);
            BlockState s = mc.world.getBlockState(p);
            if (s.isAir() || s.getHardness(mc.world, p) < 0) continue;
            double d = p.toCenterPos().squaredDistanceTo(mc.player.getEyePos());
            if (d > range.get() * range.get()) continue;
            if (d < bestDist) { bestDist = d; best = p; }
        }
        if (best == null) return;
        mc.interactionManager.updateBlockBreakingProgress(best, Direction.UP);
        mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
    }
}
