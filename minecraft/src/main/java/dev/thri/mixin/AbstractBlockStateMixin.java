package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.module.modules.world.XRay;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {

    @Inject(method = "isOpaque", at = @At("HEAD"), cancellable = true)
    private void thri$opaque(CallbackInfoReturnable<Boolean> cir) {
        XRay x = Thri.MODULES == null ? null : Thri.MODULES.get(XRay.class);
        if (x == null || !x.isEnabled()) return;
        if (!x.visible((BlockState)(Object)this)) cir.setReturnValue(false);
    }
}
