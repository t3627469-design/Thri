package net.minecraft.world;
public interface CollisionView extends BlockView {
    default boolean canPlace(net.minecraft.block.BlockState state, net.minecraft.util.math.BlockPos pos, net.minecraft.block.ShapeContext context) { return false; }
}
