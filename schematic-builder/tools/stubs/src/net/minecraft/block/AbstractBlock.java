package net.minecraft.block;
public abstract class AbstractBlock {
    public abstract static class AbstractBlockState extends net.minecraft.state.State<Block, BlockState> {
        public Block getBlock() { return null; }
        public boolean isAir() { return false; }
        public boolean isReplaceable() { return false; }
        public net.minecraft.fluid.FluidState getFluidState() { return null; }
        public BlockState rotate(net.minecraft.util.BlockRotation rotation) { return null; }
        public BlockState mirror(net.minecraft.util.BlockMirror mirror) { return null; }
    }
}
