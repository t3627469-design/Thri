package net.minecraft.item;
public class ItemPlacementContext extends ItemUsageContext {
    public ItemPlacementContext(net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand, ItemStack stack, net.minecraft.util.hit.BlockHitResult hitResult) {}
    public net.minecraft.util.math.BlockPos getBlockPos() { return null; }
    public boolean canPlace() { return false; }
}
