package net.minecraft;
public class class_3532 {
    public static float method_15363(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    public static float method_15393(float v) { float r = v % 360.0f; if (r >= 180.0f) r -= 360.0f; if (r < -180.0f) r += 360.0f; return r; }
}
