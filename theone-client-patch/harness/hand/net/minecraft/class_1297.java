package net.minecraft;
public class class_1297 {
    public double x, y, z, vx, vy, vz; public float yaw, pitch; public boolean onGround;
    public double method_23317() { return x; }
    public double method_23318() { return y; }
    public double method_23321() { return z; }
    public float method_36454() { return yaw; }
    public float method_36455() { return pitch; }
    public void method_36456(float v) { yaw = v; }
    public void method_36457(float v) { pitch = v; }
    public boolean method_24828() { return onGround; }
    public class_2338 method_24515() { return class_2338.method_49637(x, y + 0.001, z); }
    public class_243 method_33571() { return new class_243(x, y + 1.62, z); }
    public void method_5814(double nx, double ny, double nz) { x = nx; y = ny; z = nz; }
    public double method_5739(class_1297 o) { return Math.hypot(x - o.x, z - o.z); }
    public boolean method_5805() { return true; }
}
