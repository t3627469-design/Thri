package net.minecraft;
import java.util.*;
public class class_638 implements class_1922 {
    public final Map<class_2338, class_2680> blocks = new HashMap<>();
    public class_2680 method_8320(class_2338 p) { class_2680 s = blocks.get(p); return s == null ? AIR() : s; }
    public boolean method_8393(int cx, int cz) { return true; }
    public class_3610 method_8316(class_2338 p) { return class_3610.EMPTY; }
    public class_3965 method_17742(class_3959 ctx) { return Sim.raycast(this, ctx.start, ctx.end); }
    public static class_2680 AIR() { return new class_2680(class_2246.AIR, Map.of()); }
    public void method_8501(class_2338 p, class_2680 s) { if (s.method_26215()) blocks.remove(p); else blocks.put(p, s); }
}
