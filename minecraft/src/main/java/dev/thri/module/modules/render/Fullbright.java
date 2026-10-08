package dev.thri.module.modules.render;

import dev.thri.module.Category;
import dev.thri.module.Module;

public class Fullbright extends Module {
    private double saved;
    public Fullbright() { super("Fullbright", Category.RENDER, 0); }

    @Override protected void onEnable()  { saved = mc.options.getGamma().getValue(); mc.options.getGamma().setValue(16.0); }
    @Override protected void onDisable() { mc.options.getGamma().setValue(saved); }
}
