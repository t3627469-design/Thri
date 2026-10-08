package dev.thri.module.modules.combat;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import dev.thri.util.RotationUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

public class KillAura extends Module {
    public final Setting<Double> range    = add(new Setting<>("Range", 4.2, 2.0, 6.0));
    public final Setting<Double> fov      = add(new Setting<>("FOV", 180.0, 10.0, 360.0));
    public final Setting<Integer> cps     = add(new Setting<>("CPS", 10, 1, 20));
    public final Setting<Boolean> players = add(new Setting<>("Players", true));
    public final Setting<Boolean> mobs    = add(new Setting<>("Mobs", true));
    public final Setting<Boolean> rotate  = add(new Setting<>("Rotate", true));
    public final Setting<Boolean> swing   = add(new Setting<>("Swing", true));

    private long lastHit;

    public KillAura() { super("KillAura", Category.COMBAT, GLFW.GLFW_KEY_R); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        long delay = 1000L / Math.max(1, cps.get());
        if (now - lastHit < delay) return;

        LivingEntity target = pick();
        if (target == null) return;

        if (rotate.get()) {
            float[] r = RotationUtil.rotationsToEntity(target, target.getEyeHeight(target.getPose()));
            mc.player.setYaw(r[0]);
            mc.player.setPitch(r[1]);
        }

        mc.interactionManager.attackEntity(mc.player, target);
        if (swing.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        lastHit = now;
    }

    private LivingEntity pick() {
        double best = range.get() * range.get();
        LivingEntity found = null;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == mc.player || le.isRemoved() || le.isDead()) continue;
            if (le instanceof ArmorStandEntity) continue;
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
