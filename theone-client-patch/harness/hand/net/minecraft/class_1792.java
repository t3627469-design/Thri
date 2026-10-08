package net.minecraft;
import java.util.*;
public class class_1792 implements class_1935 {
    private static final Map<class_2248, class_1792> BY_BLOCK = new HashMap<>();
    public static final Map<String, class_1792> BY_NAME = new LinkedHashMap<>();
    public final String id;
    public class_1792(String id) { this.id = id; BY_NAME.put(id, this); }
    public static class_1792 itemFor(class_2248 b) { return BY_BLOCK.computeIfAbsent(b, k -> new class_1747(k)); }
    @Override public String toString() { return id; }
}
