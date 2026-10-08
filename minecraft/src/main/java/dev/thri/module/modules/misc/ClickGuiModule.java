package dev.thri.module.modules.misc;

import dev.thri.module.Category;
import dev.thri.module.Module;
import org.lwjgl.glfw.GLFW;

public class ClickGuiModule extends Module {
    public ClickGuiModule() { super("ClickGUI", Category.MISC, GLFW.GLFW_KEY_RIGHT_SHIFT); }
}
