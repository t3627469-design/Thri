package dev.thri.module.modules.combat;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

public class Reach extends Module {
    public final Setting<Double> block  = add(new Setting<>("Block", 5.5, 4.5, 7.0));
    public final Setting<Double> entity = add(new Setting<>("Entity", 4.0, 3.0, 6.0));
    private double savedBlock, savedEntity;

    public Reach() { super("Reach", Category.COMBAT, 0); }

    public float distance() { return block.get().floatValue(); }

    @Override protected void onEnable() {
        if (mc.player == null) return;
        EntityAttributeInstance b = mc.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        EntityAttributeInstance e = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (b != null) savedBlock  = b.getBaseValue();
        if (e != null) savedEntity = e.getBaseValue();
    }

    @Override public void onTick() {
        if (mc.player == null) return;
        EntityAttributeInstance b = mc.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        EntityAttributeInstance e = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (b != null) b.setBaseValue(block.get());
        if (e != null) e.setBaseValue(entity.get());
    }

    @Override protected void onDisable() {
        if (mc.player == null) return;
        EntityAttributeInstance b = mc.player.getAttributeInstance(EntityAttributes.BLOCK_INTERACTION_RANGE);
        EntityAttributeInstance e = mc.player.getAttributeInstance(EntityAttributes.ENTITY_INTERACTION_RANGE);
        if (b != null && savedBlock  > 0) b.setBaseValue(savedBlock);
        if (e != null && savedEntity > 0) e.setBaseValue(savedEntity);
    }
}
