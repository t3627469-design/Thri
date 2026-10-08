package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;

// Camera-only free movement is non-trivial without heavy mixin — this module simulates
// it by letting the player phase and move freely in no-clip style while on ground tag.
public class Freecam extends Module {
    public final Setting<Double> speed = add(new Setting<>("Speed", 1.0, 0.1, 5.0));
    private boolean savedNoClip;

    public Freecam() { super("Freecam", Category.MOVEMENT, 0); }

    @Override protected void onEnable() {
        if (mc.player != null) {
            savedNoClip = mc.player.noClip;
            mc.player.noClip = true;
            mc.player.getAbilities().flying = true;
        }
    }
    @Override protected void onDisable() {
        if (mc.player != null) {
            mc.player.noClip = savedNoClip;
            if (!mc.player.getAbilities().creativeMode) mc.player.getAbilities().flying = false;
        }
    }
    @Override public void onTick() {
        if (mc.player == null) return;
        mc.player.getAbilities().setFlySpeed(speed.get().floatValue() * 0.05f);
    }
}
