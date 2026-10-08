package net.minecraft;
public class class_243 {
    public final double field_1352, field_1351, field_1350;
    public class_243(double x, double y, double z) { field_1352 = x; field_1351 = y; field_1350 = z; }
    public static class_243 method_24953(class_2382 v) { return new class_243(v.method_10263() + 0.5, v.method_10264() + 0.5, v.method_10260() + 0.5); }
    public class_243 method_1019(class_243 o) { return new class_243(field_1352 + o.field_1352, field_1351 + o.field_1351, field_1350 + o.field_1350); }
    public class_243 method_1020(class_243 o) { return new class_243(field_1352 - o.field_1352, field_1351 - o.field_1351, field_1350 - o.field_1350); }
    public class_243 method_1021(double s) { return new class_243(field_1352 * s, field_1351 * s, field_1350 * s); }
    public class_243 method_1031(double x, double y, double z) { return new class_243(field_1352 + x, field_1351 + y, field_1350 + z); }
    public double method_1022(class_243 o) { return Math.sqrt(method_1025(o)); }
    public double method_1025(class_243 o) { double dx = field_1352 - o.field_1352, dy = field_1351 - o.field_1351, dz = field_1350 - o.field_1350; return dx*dx + dy*dy + dz*dz; }
    public double method_1026(class_243 o) { return field_1352 * o.field_1352 + field_1351 * o.field_1351 + field_1350 * o.field_1350; }
}
