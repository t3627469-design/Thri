package net.minecraft;
import java.util.List;
public class class_2769 {
    private final String name; public final List<Comparable> values;
    public class_2769(String name, List<? extends Comparable> values) { this.name = name; this.values = List.copyOf(values); }
    public String method_11899() { return name; }
    @Override public String toString() { return name; }
}
