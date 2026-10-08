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
        if (window != MinecraftClient.getInstance().getWindow().getHandle()) return;
        if (action != GLFW.GLFW_PRESS) return;
        if (MinecraftClient.getInstance().currentScreen != null
                && !(MinecraftClient.getInstance().currentScreen instanceof ClickGuiScreen)) return;
        if (Thri.MODULES == null) return;
        ClickGuiModule gui = Thri.MODULES.get(ClickGuiModule.class);
        if (gui != null && key == gui.getKey()) {
            MinecraftClient.getInstance().setScreen(new ClickGuiScreen());
            return;
        }
        Thri.MODULES.onKey(key);
    }
}
