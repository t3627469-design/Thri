package net.minecraft.fluid;
public final class FluidState extends net.minecraft.state.State<Fluid, FluidState> {
    public boolean isEmpty() { return false; }
    public boolean isStill() { return false; }
}
