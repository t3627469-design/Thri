package dev.thri.event.events;

import dev.thri.event.Event;
import net.minecraft.client.util.math.MatrixStack;

public class RenderWorldEvent extends Event {
    public final MatrixStack matrices;
    public final float tickDelta;
    public final double camX, camY, camZ;
    public RenderWorldEvent(MatrixStack m, float t, double x, double y, double z) {
        this.matrices = m; this.tickDelta = t; this.camX = x; this.camY = y; this.camZ = z;
    }
}
