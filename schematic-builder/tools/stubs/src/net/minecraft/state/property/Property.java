package net.minecraft.state.property;
public abstract class Property<T extends Comparable<T>> {
    public String getName() { return null; }
    public abstract java.util.Optional<T> parse(String name);
    public abstract String name(T value);
}
