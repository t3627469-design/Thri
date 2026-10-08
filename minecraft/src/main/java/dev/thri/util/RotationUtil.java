package dev.thri.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class RotationUtil {
    private RotationUtil() {}

    public static float[] rotationsTo(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dy, h) * 180.0 / Math.PI);
        return new float[]{ wrap(yaw), MathHelper.clamp(pitch, -90f, 90f) };
    }

    public static float[] rotationsToEntity(Entity target, double eyeOffsetY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return new float[]{0, 0};
        Vec3d eye = mc.player.getEyePos();
        Box b = target.getBoundingBox();
        Vec3d pick = new Vec3d(
                clamp(eye.x, b.minX, b.maxX),
                clamp(eye.y, b.minY + eyeOffsetY, b.maxY),
                clamp(eye.z, b.minZ, b.maxZ));
        return rotationsTo(eye, pick);
    }

    public static float wrap(float yaw) {
        yaw %= 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw <  -180f) yaw += 360f;
        return yaw;
    }

    public static float step(float from, float to, float max) {
        float d = MathHelper.wrapDegrees(to - from);
        d = MathHelper.clamp(d, -max, max);
        return wrap(from + d);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
