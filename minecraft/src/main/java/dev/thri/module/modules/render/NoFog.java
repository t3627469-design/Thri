package dev.thri.module.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.thri.module.Category;
import dev.thri.module.Module;

public class NoFog extends Module {
    public NoFog() { super("NoFog", Category.RENDER, 0); }
    public void apply() {
        if (!isEnabled()) return;
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
    }
}
