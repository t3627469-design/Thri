package net.minecraft;
public class class_2960 {
    private final String path;
    private class_2960(String p) { path = p; }
    public static class_2960 method_60654(String s) { return new class_2960(s); }
    public String method_12832() { return path; }
    @Override public boolean equals(Object o) { return o instanceof class_2960 i && i.path.equals(path); }
    @Override public int hashCode() { return path.hashCode(); }
    @Override public String toString() { return path; }
}
