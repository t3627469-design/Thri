package net.minecraft;
public class class_1799 {
    public static final class_1799 EMPTY = new class_1799();
    private class_1792 item; private int count;
    public class_1799() { item = null; count = 0; }
    public class_1799(class_1935 c) { item = (class_1792) c; count = 1; }
    public class_1792 method_7909() { return item; }
    public int method_7947() { return count; }
    public void method_7939(int n) { count = n; if (n <= 0) item = null; }
    public boolean method_7960() { return count <= 0 || item == null; }
    public int method_7914() { return 64; }
    public void shrink() { if (--count <= 0) { count = 0; item = null; } }
}
