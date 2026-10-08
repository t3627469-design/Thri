package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.event.events.MotionUpdateEvent;
import dev.thri.event.events.MotionUpdateEvent.Phase;
import dev.thri.module.modules.movement.NoFall;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void thri$preMotion(CallbackInfo ci) {
        Thri.BUS.post(new MotionUpdateEvent(Phase.PRE));
        if (Thri.MODULES != null) {
            NoFall nf = Thri.MODULES.get(NoFall.class);
            if (nf != null) nf.touchGroundTag((ClientPlayerEntity)(Object)this);
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void thri$postMotion(CallbackInfo ci) {
        Thri.BUS.post(new MotionUpdateEvent(Phase.POST));
    }
}
