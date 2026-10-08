package dev.thri.module.modules.render;

import dev.thri.module.Category;
import dev.thri.module.Module;

// Hook point for a mixin on EntityRenderDispatcher to flip depth-test off before entity render.
// Keeping flag only; a RenderLayer-style mixin can read `isEnabled()` to apply.
public class Chams extends Module {
    public Chams() { super("Chams", Category.RENDER, 0); }
}
