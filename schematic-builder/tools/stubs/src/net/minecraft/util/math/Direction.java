package net.minecraft.util.math;
public enum Direction {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;
    public int getOffsetX() { return 0; }
    public int getOffsetY() { return 0; }
    public int getOffsetZ() { return 0; }
    public Direction getOpposite() { return null; }
}
