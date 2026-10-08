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

public class ESP extends Module {
    public final Setting<Boolean> players = add(new Setting<>("Players", true));
    public final Setting<Boolean> mobs    = add(new Setting<>("Mobs", true));
    public final Setting<Boolean> items   = add(new Setting<>("Items", false));
    public final Setting<Double>  range   = add(new Setting<>("Range", 128.0, 16.0, 256.0));

    public ESP() {
        super("ESP", Category.RENDER, 0);
        Thri.BUS.subscribe(RenderWorldEvent.class, this::onRender);
    }

    private void onRender(RenderWorldEvent e) {
        if (!isEnabled() || mc.world == null || mc.player == null) return;
        double r2 = range.get() * range.get();
        for (Entity en : mc.world.getEntities()) {
            if (en == mc.player) continue;
            if (mc.player.squaredDistanceTo(en) > r2) continue;
            float R=1,G=1,B=1;
            if (en instanceof PlayerEntity) { if (!players.get()) continue; R=1;G=.3f;B=.3f; }
            else if (en instanceof LivingEntity) { if (!mobs.get()) continue; R=1;G=.9f;B=.2f; }
            else { if (!items.get()) continue; R=.3f;G=.8f;B=1f; }
            RenderUtil.drawBoxOutline(e.matrices, en.getBoundingBox(), R,G,B,1f, e.camX, e.camY, e.camZ);
        }
    }
}
