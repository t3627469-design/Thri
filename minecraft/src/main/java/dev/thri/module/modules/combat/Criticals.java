package dev.thri.module.modules.combat;

import dev.thri.event.events.PacketEvent;
import dev.thri.Thri;
import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public class Criticals extends Module {
    public Criticals() {
        super("Criticals", Category.COMBAT, 0);
        Thri.BUS.subscribe(PacketEvent.class, this::onPkt);
    }

    private void onPkt(PacketEvent e) {
        if (!isEnabled() || mc.player == null) return;
        if (e.direction() != PacketEvent.Direction.OUTBOUND) return;
        if (!(e.packet() instanceof PlayerInteractEntityC2SPacket p)) return;
        if (!mc.player.isOnGround() || mc.player.isSubmergedInWater()
                || mc.player.isClimbing() || mc.player.hasVehicle()) return;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, false));
        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
    }
}
