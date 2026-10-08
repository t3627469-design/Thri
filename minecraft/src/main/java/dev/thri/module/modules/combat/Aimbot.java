package dev.thri.module.modules.combat;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import dev.thri.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

public class Aimbot extends Module {
    public final Setting<Double>  range   = add(new Setting<>("Range", 60.0, 5.0, 128.0));
    public final Setting<Double>  fov     = add(new Setting<>("FOV", 60.0, 5.0, 180.0));
    public final Setting<Double>  smooth  = add(new Setting<>("Smooth", 25.0, 1.0, 90.0));
    public final Setting<Boolean> onRMB   = add(new Setting<>("HoldRightClick", true));
    public final Setting<Boolean> players = add(new Setting<>("Players", true));
    public final Setting<Boolean> mobs    = add(new Setting<>("Mobs", true));

    public Aimbot() { super("Aimbot", Category.COMBAT, GLFW.GLFW_KEY_B); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (onRMB.get() && !mc.options.useKey.isPressed() && !mc.options.attackKey.isPressed()) return;

        LivingEntity t = pick();
        if (t == null) return;

        float[] r = RotationUtil.rotationsToEntity(t, t.getEyeHeight(t.getPose()));
        float step = smooth.get().floatValue();
        mc.player.setYaw(RotationUtil.step(mc.player.getYaw(), r[0], step));
        mc.player.setPitch(RotationUtil.step(mc.player.getPitch(), r[1], step));
    }

    private LivingEntity pick() {
        double best = range.get() * range.get();
        LivingEntity found = null;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || le.isRemoved() || le.isDead()) continue;
            if (le instanceof PlayerEntity p) {
                if (!players.get()) continue;
                if (p.isSpectator() || p.isCreative()) continue;
            } else if (!mobs.get()) continue;
            double d = mc.player.squaredDistanceTo(le);
            if (d > best) continue;
            float[] r = RotationUtil.rotationsToEntity(le, le.getEyeHeight(le.getPose()));
            float dy = Math.abs(RotationUtil.wrap(r[0] - mc.player.getYaw()));
            if (dy > fov.get() / 2.0) continue;
            best = d; found = le;
        }
        return found;
    }
}
