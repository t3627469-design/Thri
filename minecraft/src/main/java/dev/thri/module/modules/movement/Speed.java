package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.util.math.Vec3d;

public class Speed extends Module {
    public final Setting<Double> mult = add(new Setting<>("Mult", 1.6, 1.0, 5.0));
    public Speed() { super("Speed", Category.MOVEMENT, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || !mc.player.isOnGround()) return;
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        double fwd = 0, str = 0;
        if (mc.options.forwardKey.isPressed()) fwd += 1;
        if (mc.options.backKey.isPressed())    fwd -= 1;
        if (mc.options.leftKey.isPressed())    str += 1;
        if (mc.options.rightKey.isPressed())   str -= 1;
        if (fwd == 0 && str == 0) return;
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double v = 0.2873 * mult.get();
        double mx = fwd * -sin + str * cos;
        double mz = fwd *  cos + str * sin;
        double len = Math.sqrt(mx*mx + mz*mz);
        Vec3d cur = mc.player.getVelocity();
        mc.player.setVelocity(mx / len * v, cur.y, mz / len * v);
    }
}
