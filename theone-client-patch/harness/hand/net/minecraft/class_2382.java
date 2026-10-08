package net.minecraft;
public class class_2382 {
    protected final int x, y, z;
    public class_2382(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int method_10263() { return x; }
    public int method_10264() { return y; }
    public int method_10260() { return z; }
    public double method_10262(class_2382 o) { double dx = x - o.method_10263(), dy = y - o.method_10264(), dz = z - o.method_10260(); return dx*dx + dy*dy + dz*dz; }
    public boolean method_19771(class_2382 o, double d) { return method_10262(o) < d * d; }
}
