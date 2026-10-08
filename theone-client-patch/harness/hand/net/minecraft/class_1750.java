package net.minecraft;
public class class_1750 {
    public final class_1838 ctx;
    public class_1750(class_1838 c) { ctx = c; }
    /** The direction the player is looking horizontally (vanilla getHorizontalPlayerFacing). */
    public class_2350 horizontalFacing() {
        double rad = Math.toRadians(ctx.player.method_36454());
        double dx = -Math.sin(rad), dz = Math.cos(rad);
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? class_2350.EAST : class_2350.WEST;
        return dz > 0 ? class_2350.SOUTH : class_2350.NORTH;
    }
    /** The block being placed: the clicked block's neighbour on the clicked face. */
    public class_2338 placePos() { return ctx.hit.method_17777().method_10093(ctx.hit.method_17780()); }
    /** Height of the click inside the block being placed, 0..1 (vanilla: hitPos.y - pos.y). */
    public double hitY() { return ctx.hit.method_17784().field_1351 - placePos().method_10264(); }
    public class_2350 side() { return ctx.hit.method_17780(); }
}
