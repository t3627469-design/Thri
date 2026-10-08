package net.minecraft;
public class class_1661 {
    public final class_1799[] stacks = new class_1799[36];
    public int selected;
    public class_1661() { for (int i = 0; i < 36; i++) stacks[i] = new class_1799(); }
    public class_1799 method_5438(int i) { return stacks[i]; }
    public void method_5447(int i, class_1799 s) { stacks[i] = s; }
    public int method_67532() { return selected; }
    public void method_67533(int s) { selected = s; }
    public int method_7376() { return 0; }
}
