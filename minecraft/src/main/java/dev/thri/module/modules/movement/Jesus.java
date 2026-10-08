package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class Jesus extends Module {
    public Jesus() { super("Jesus", Category.MOVEMENT, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        BlockPos below = BlockPos.ofFloored(mc.player.getX(), mc.player.getY() - 0.03, mc.player.getZ());
        FluidState f = mc.world.getFluidState(below);
        if (f.isEmpty() || mc.options.sneakKey.isPressed()) return;
        Vec3d v = mc.player.getVelocity();
        if (v.y < 0) mc.player.setVelocity(v.x, 0.08, v.z);
        mc.player.setOnGround(true);
    }
}
