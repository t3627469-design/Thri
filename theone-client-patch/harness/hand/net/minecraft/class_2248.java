package net.minecraft;
import java.util.*;
public class class_2248 {
    public static final Map<String, class_2248> REGISTRY = new LinkedHashMap<>();
    public final String id; public final List<class_2769> properties; private final boolean air, fluid;
    public final Map<class_2769, Comparable> defaults = new LinkedHashMap<>();
    public class_2248(String id, boolean air, boolean fluid, class_2769... props) {
        this.id = id; this.air = air; this.fluid = fluid; this.properties = List.of(props);
        for (class_2769 p : props) defaults.put(p, p.values.get(0));
        REGISTRY.put(id, this);
    }
    public boolean isAir() { return air; }
    public boolean isFluid() { return fluid; }
    public class_2680 method_9564() { return new class_2680(this, defaults); }
    public class_1792 method_8389() { return class_1792.itemFor(this); }
    /** Vanilla-like placement: facing from the player's look, half from the hit height, axis from the clicked face. */
    public class_2680 method_9605(class_1750 ctx) {
        class_2680 s = method_9564();
        for (class_2769 p : properties) {
            switch (p.method_11899()) {
                case "facing" -> s = s.withProp(p, ctx.horizontalFacing());
                case "half", "type" -> s = s.withProp(p, halfTop(ctx) ? "top" : "bottom");
                case "axis" -> s = s.withProp(p, ctx.side().method_10166());
                default -> { }
            }
        }
        return s;
    }
    /** Vanilla: clicking a floor face gives the top half, a ceiling face the bottom, a side the upper half if clicked above middle. */
    private static boolean halfTop(class_1750 ctx) {
        if (ctx.side() == class_2350.DOWN) return true;
        if (ctx.side() == class_2350.UP) return false;
        return ctx.hitY() > 0.5;
    }
    @Override public String toString() { return id; }
}
