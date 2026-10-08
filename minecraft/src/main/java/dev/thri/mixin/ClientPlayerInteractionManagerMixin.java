package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.module.modules.world.FastBreak;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"))
    private void thri$fastBreakStart(BlockPos pos, net.minecraft.util.math.Direction dir,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (Thri.MODULES == null) return;
        FastBreak fb = Thri.MODULES.get(FastBreak.class);
        if (fb != null && fb.isEnabled()) fb.boost((ClientPlayerInteractionManager)(Object)this);
    }
}
