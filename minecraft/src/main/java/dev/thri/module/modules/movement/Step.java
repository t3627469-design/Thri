package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public class Step extends Module {
    public final Setting<Double> height = add(new Setting<>("Height", 1.5, 0.6, 4.0));
    public Step() { super("Step", Category.MOVEMENT, 0); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        EntityAttributeInstance a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (a != null) a.setBaseValue(height.get());
    }
    @Override protected void onDisable() {
        if (mc.player == null) return;
        EntityAttributeInstance a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (a != null) a.setBaseValue(0.6);
    }
}
