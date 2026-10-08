package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.gui.ClickGuiScreen;
import dev.thri.module.modules.misc.ClickGuiModule;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void thri$onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (window != mc.getWindow().getHandle()) return;
        if (action != GLFW.GLFW_PRESS) return;
        if (Thri.MODULES == null) return;

        ClickGuiModule gui = Thri.MODULES.get(ClickGuiModule.class);
        int guiKey = gui == null ? 0 : gui.getKey();

        // RSHIFT toggles the ClickGUI from either state
        if (guiKey != 0 && key == guiKey) {
            if (mc.currentScreen instanceof ClickGuiScreen) mc.setScreen(null);
            else if (mc.currentScreen == null)             mc.setScreen(new ClickGuiScreen());
            return;
        }

        // Other hotkeys fire only in gameplay, never while any screen is open
        if (mc.currentScreen != null) return;
        Thri.MODULES.onKey(key);
    }
}
