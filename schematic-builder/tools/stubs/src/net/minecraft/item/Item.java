package net.minecraft.item;
public class Item implements ItemConvertible {
    public Item asItem() { return this; }
    public ItemStack getDefaultStack() { return null; }
}
