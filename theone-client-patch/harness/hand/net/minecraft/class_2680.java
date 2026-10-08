package net.minecraft;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
public class class_2680 {
    private final class_2248 block; private final Map<class_2769, Comparable> props;
    public class_2680(class_2248 block, Map<class_2769, Comparable> props) { this.block = block; this.props = new LinkedHashMap<>(props); }
    public class_2248 method_26204() { return block; }
    public Comparable method_11654(class_2769 p) { return props.get(p); }
    public boolean method_28498(class_2769 p) { return props.containsKey(p); }
    public Map<class_2769, Comparable> method_11656() { return props; }
    public Collection<class_2769> method_28501() { return props.keySet(); }
    public boolean method_26215() { return block.isAir(); }
    public boolean method_45474() { return block.isAir() || block.isFluid(); }
    public class_3610 method_26227() { return class_3610.EMPTY; }
    public class_265 method_26220(class_1922 view, class_2338 pos) { return block.isAir() ? class_265.EMPTY : class_265.FULL; }
    public class_2680 withProp(class_2769 p, Comparable v) { Map<class_2769, Comparable> m = new LinkedHashMap<>(props); m.put(p, v); return new class_2680(block, m); }
    @Override public boolean equals(Object o) { return o instanceof class_2680 s && s.block == block && s.props.equals(props); }
    @Override public int hashCode() { return block.hashCode() * 31 + props.hashCode(); }
    @Override public String toString() { return block + props.toString(); }
}
