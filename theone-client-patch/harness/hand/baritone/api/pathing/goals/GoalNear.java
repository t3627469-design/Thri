package baritone.api.pathing.goals;
public class GoalNear implements Goal {
    public final net.minecraft.class_2338 pos; public final int range;
    public GoalNear(net.minecraft.class_2338 pos, int range) { this.pos = pos; this.range = range; }
    public boolean isInGoal(int x, int y, int z) {
        double dx = x - pos.method_10263(), dy = y - pos.method_10264(), dz = z - pos.method_10260();
        return dx * dx + dy * dy + dz * dz <= (double) range * range;
    }
}
