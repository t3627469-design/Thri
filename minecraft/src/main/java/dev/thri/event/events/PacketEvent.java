package dev.thri.event.events;

import dev.thri.event.Event;
import net.minecraft.network.packet.Packet;

public class PacketEvent extends Event {
    public enum Direction { INBOUND, OUTBOUND }
    private final Direction dir;
    private Packet<?> packet;
    public PacketEvent(Direction d, Packet<?> p) { this.dir = d; this.packet = p; }
    public Direction direction() { return dir; }
    public Packet<?> packet() { return packet; }
    public void setPacket(Packet<?> p) { this.packet = p; }
}
