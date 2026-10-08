package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class Fly extends Module {
    public enum Mode { Vanilla, Motion, Creative }
    public final Setting<Mode>   mode  = add(new Setting<>("Mode", Mode.Motion));
    public final Setting<Double> speed = add(new Setting<>("Speed", 1.0, 0.1, 5.0));

    public Fly() { super("Fly", Category.MOVEMENT, GLFW.GLFW_KEY_F); }

    @Override protected void onDisable() {
        if (mc.player == null) return;
        if (mc.player.getAbilities().creativeMode) return;
        mc.player.getAbilities().allowFlying = false;
        mc.player.getAbilities().flying = false;
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        switch (mode.get()) {
            case Creative -> {
                mc.player.getAbilities().allowFlying = true;
                mc.player.getAbilities().flying = true;
                mc.player.getAbilities().setFlySpeed(speed.get().floatValue() * 0.05f);
            }
            case Vanilla -> {
                mc.player.setVelocity(0, 0, 0);
                mc.player.setOnGround(false);
                double v = speed.get();
                if (mc.options.jumpKey.isPressed())  mc.player.setVelocity(mc.player.getVelocity().add(0, v, 0));
                if (mc.options.sneakKey.isPressed()) mc.player.setVelocity(mc.player.getVelocity().add(0, -v, 0));
                move(v);
            }
            case Motion -> {
                mc.player.setVelocity(mc.player.getVelocity().x, 0, mc.player.getVelocity().z);
                double v = speed.get();
                double vy = 0;
                if (mc.options.jumpKey.isPressed())  vy += v;
                if (mc.options.sneakKey.isPressed()) vy -= v;
                Vec3d cur = mc.player.getVelocity();
                mc.player.setVelocity(cur.x, vy, cur.z);
                move(v);
            }
        }
    }

    private void move(double v) {
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        double fwd = 0, str = 0;
        if (mc.options.forwardKey.isPressed()) fwd += 1;
        if (mc.options.backKey.isPressed())    fwd -= 1;
        if (mc.options.leftKey.isPressed())    str += 1;
        if (mc.options.rightKey.isPressed())   str -= 1;
        if (fwd == 0 && str == 0) { mc.player.setVelocity(0, mc.player.getVelocity().y, 0); return; }
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double mx = fwd * -sin + str * cos;
        double mz = fwd *  cos + str * sin;
        double len = Math.sqrt(mx*mx + mz*mz);
        mc.player.setVelocity(mx / len * v, mc.player.getVelocity().y, mz / len * v);
    }
}
