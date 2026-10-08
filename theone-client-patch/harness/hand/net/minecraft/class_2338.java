package net.minecraft;
public class class_2338 extends class_2382 {
    public class_2338(int x, int y, int z) { super(x, y, z); }
    public static class_2338 method_49637(double x, double y, double z) { return new class_2338((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }
    public class_2338 method_10062() { return this; }
    public class_2338 method_10069(int dx, int dy, int dz) { return new class_2338(x + dx, y + dy, z + dz); }
    public class_2338 method_10074() { return new class_2338(x, y - 1, z); }
    public class_2338 method_10084() { return new class_2338(x, y + 1, z); }
    public class_2338 method_10086(int n) { return new class_2338(x, y - n, z); }
    public class_2338 method_10087(int n) { return new class_2338(x, y + n, z); }
    public class_2338 method_10093(class_2350 d) { return new class_2338(x + d.method_10148(), y + d.method_10164(), z + d.method_10165()); }
    @Override public boolean equals(Object o) { return o instanceof class_2338 p && p.x == x && p.y == y && p.z == z; }
    @Override public int hashCode() { return (x * 31 + y) * 31 + z; }
    @Override public String toString() { return "(" + x + "," + y + "," + z + ")"; }
}
