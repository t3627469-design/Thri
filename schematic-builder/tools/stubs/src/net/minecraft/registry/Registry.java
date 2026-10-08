package net.minecraft.registry;
public interface Registry<T> {
    net.minecraft.util.Identifier getId(T value);
    boolean containsId(net.minecraft.util.Identifier id);
    T get(net.minecraft.util.Identifier id);
}
