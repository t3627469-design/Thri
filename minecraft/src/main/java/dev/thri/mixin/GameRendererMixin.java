package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.module.modules.render.NoFog;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void thri$renderWorldHead(CallbackInfo ci) {
        if (Thri.MODULES == null) return;
        NoFog nf = Thri.MODULES.get(NoFog.class);
        if (nf != null) nf.apply();
    }
}
