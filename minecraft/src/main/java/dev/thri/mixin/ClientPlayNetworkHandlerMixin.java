package dev.thri.mixin;

import dev.thri.Thri;
import dev.thri.event.events.PacketEvent;
import dev.thri.module.modules.combat.AntiKnockback;
import dev.thri.module.modules.combat.Velocity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onEntityVelocityUpdate", at = @At("HEAD"), cancellable = true)
    private void thri$velocity(EntityVelocityUpdateS2CPacket p, CallbackInfo ci) {
        if (Thri.MODULES == null) return;
        Velocity v = Thri.MODULES.get(Velocity.class);
        AntiKnockback ak = Thri.MODULES.get(AntiKnockback.class);
        if (v != null && v.isEnabled() && v.cancelKnockback()) ci.cancel();
        else if (ak != null && ak.isEnabled()) ci.cancel();
    }

    @Inject(method = "onExplosion", at = @At("HEAD"), cancellable = true)
    private void thri$explosion(ExplosionS2CPacket p, CallbackInfo ci) {
        if (Thri.MODULES == null) return;
        Velocity v = Thri.MODULES.get(Velocity.class);
        if (v != null && v.isEnabled() && v.cancelExplosion()) ci.cancel();
    }

    @SuppressWarnings("unchecked")
    private void thri$fire(Packet<?> p) {
        Thri.BUS.post(new PacketEvent(PacketEvent.Direction.INBOUND, p));
    }

    @SuppressWarnings("unused")
    private ClientPlayPacketListener thri$self() { return (ClientPlayPacketListener)(Object) this; }
}
