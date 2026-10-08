package dev.thri.module.modules.render;

import dev.thri.Thri;
import dev.thri.event.events.RenderWorldEvent;
import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import dev.thri.util.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class Tracers extends Module {
    public final Setting<Boolean> players = add(new Setting<>("Players", true));
    public final Setting<Boolean> mobs    = add(new Setting<>("Mobs", false));

    public Tracers() {
        super("Tracers", Category.RENDER, 0);
        Thri.BUS.subscribe(RenderWorldEvent.class, this::onRender);
    }

    private void onRender(RenderWorldEvent e) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        Vec3d origin = mc.player.getEyePos();
        for (Entity en : mc.world.getEntities()) {
            if (en == mc.player) continue;
            float R=1,G=1,B=1;
            if (en instanceof PlayerEntity) { if (!players.get()) continue; R=1;G=.3f;B=.3f; }
            else if (en instanceof LivingEntity) { if (!mobs.get()) continue; R=1;G=.9f;B=.2f; }
            else continue;
            Vec3d c = en.getBoundingBox().getCenter();
            RenderUtil.drawLine(e.matrices, origin.x, origin.y, origin.z, c.x, c.y, c.z, R,G,B,1f, e.camX, e.camY, e.camZ);
        }
    }
}
