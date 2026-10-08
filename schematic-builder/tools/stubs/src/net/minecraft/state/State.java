package net.minecraft.state;
import net.minecraft.state.property.Property;
public abstract class State<O, S> {
    public <T extends Comparable<T>> T get(Property<T> property) { return null; }
    public <T extends Comparable<T>, V extends T> S with(Property<T> property, V value) { return null; }
    public <T extends Comparable<T>> boolean contains(Property<T> property) { return false; }
    public java.util.Collection<Property<?>> getProperties() { return null; }
}
