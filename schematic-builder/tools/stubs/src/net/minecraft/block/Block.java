package net.minecraft.block;
public class Block extends AbstractBlock implements net.minecraft.item.ItemConvertible {
    public final BlockState getDefaultState() { return null; }
    public net.minecraft.state.StateManager<Block, BlockState> getStateManager() { return null; }
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) { return null; }
    public net.minecraft.item.Item asItem() { return null; }
}
