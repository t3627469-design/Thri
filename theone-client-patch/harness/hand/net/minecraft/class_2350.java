package net.minecraft;
public enum class_2350 {
    DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
    private final int x, y, z;
    class_2350(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    public int method_10148() { return x; }
    public int method_10164() { return y; }
    public int method_10165() { return z; }
    public class_2350 method_10153() { return switch (this) { case DOWN -> UP; case UP -> DOWN; case NORTH -> SOUTH; case SOUTH -> NORTH; case WEST -> EAST; case EAST -> WEST; }; }
    public class_2350$class_2351 method_10166() { return switch (this) { case WEST, EAST -> class_2350$class_2351.X; case UP, DOWN -> class_2350$class_2351.Y; default -> class_2350$class_2351.Z; }; }
}
