package dev.thri.event.events;

import dev.thri.event.Event;

public class MotionUpdateEvent extends Event {
    public enum Phase { PRE, POST }
    public final Phase phase;
    public float yaw, pitch;
    public double x, y, z;
    public boolean onGround;
    public MotionUpdateEvent(Phase p) { this.phase = p; }
}
