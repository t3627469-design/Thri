package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class NoFall extends Module {
    public NoFall() { super("NoFall", Category.MOVEMENT, 0); }

    // Called from mixin before sendMovementPackets executes.
    public void touchGroundTag(ClientPlayerEntity p) {
        if (!isEnabled()) return;
        if (p.fallDistance > 2f && !p.isOnGround()) {
            p.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, false));
            p.fallDistance = 0;
        }
    }
}
