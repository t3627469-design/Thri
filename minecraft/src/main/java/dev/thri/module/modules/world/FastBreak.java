package dev.thri.module.modules.world;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.client.network.ClientPlayerInteractionManager;

import java.lang.reflect.Field;

public class FastBreak extends Module {
    public final Setting<Integer> delay = add(new Setting<>("Delay", 0, 0, 5));

    public FastBreak() { super("FastBreak", Category.WORLD, 0); }

    public void boost(ClientPlayerInteractionManager im) {
        try {
            Field f = ClientPlayerInteractionManager.class.getDeclaredField("blockBreakingCooldown");
            f.setAccessible(true);
            f.setInt(im, delay.get());
        } catch (Throwable ignored) {}
    }
}
