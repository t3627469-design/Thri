package net.minecraft.entity;
import net.minecraft.util.math.*;
public abstract class Entity implements net.minecraft.world.entity.EntityLike {
    public boolean horizontalCollision;
    public final double getX() { return 0; }
    public final double getY() { return 0; }
    public final double getZ() { return 0; }
    public final Vec3d getEyePos() { return null; }
    public BlockPos getBlockPos() { return null; }
    public float getYaw() { return 0; }
    public float getPitch() { return 0; }
    public void setYaw(float yaw) {}
    public void setPitch(float pitch) {}
    public boolean isOnGround() { return false; }
}
