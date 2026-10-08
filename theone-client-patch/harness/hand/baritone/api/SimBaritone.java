package baritone.api;
import java.util.*;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.*;
import meteordevelopment.meteorclient.MeteorClient;

/** Stand-in for Baritone's custom-goal pathing: A* over the sim world, followed by holding keys. */
public class SimBaritone implements IBaritone, IBaritoneProvider, baritone.api.process.ICustomGoalProcess, baritone.api.behavior.IPathingBehavior, baritone.api.utils.IInputOverrideHandler {
    public static final SimBaritone PROVIDER = new SimBaritone();
    public static int pathsFound, pathsFailed;
    private Goal goal; private List<int[]> path; private int idx; private int stall; private double lastD = 1e9;

    public IBaritone getPrimaryBaritone() { return this; }
    public baritone.api.process.ICustomGoalProcess getCustomGoalProcess() { return this; }
    public baritone.api.behavior.IPathingBehavior getPathingBehavior() { return this; }
    public baritone.api.utils.IInputOverrideHandler getInputOverrideHandler() { return this; }
    public boolean isInputForcedDown(baritone.api.utils.input.Input input) { return false; }
    public boolean isActive() { return goal != null; }
    public void setGoalAndPath(Goal g) { goal = g; path = null; idx = 0; stall = 0; lastD = 1e9; }
    public boolean cancelEverything() { goal = null; path = null; release(); return true; }

    private static void release() {
        class_315 o = MeteorClient.mc.field_1690;
        o.field_1894.pressed = false; o.field_1881.pressed = false; o.field_1913.pressed = false; o.field_1849.pressed = false; o.field_1903.pressed = false;
    }

    /** Called once per tick before physics. Holds the movement keys that follow the path. */
    public void tick(Sim sim) {
        if (goal == null) return;
        class_746 p = sim.player;
        int fx = (int) Math.floor(p.x), fy = (int) Math.floor(p.y + 0.01), fz = (int) Math.floor(p.z);
        if (path == null) {
            path = astar(sim, fx, fy, fz);
            idx = 0;
            if (path == null) { pathsFailed++; goal = null; release(); return; }
            pathsFound++;
        }
        if (idx >= path.size()) { goal = null; path = null; release(); return; }
        int[] n = path.get(idx);
        double cx = n[0] + 0.5, cz = n[2] + 0.5;
        double dx = cx - p.x, dz = cz - p.z;
        double d = Math.hypot(dx, dz);
        if (d < 0.22 && Math.abs(p.y - n[1]) < 0.6) { idx++; if (idx >= path.size()) { goal = null; path = null; release(); return; } n = path.get(idx); cx = n[0] + 0.5; cz = n[2] + 0.5; dx = cx - p.x; dz = cz - p.z; d = Math.hypot(dx, dz); }
        // face the node and walk
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        p.yaw = (float) yaw;
        class_315 o = MeteorClient.mc.field_1690;
        o.field_1894.pressed = true;
        o.field_1903.pressed = n[1] > p.y + 0.3 && p.onGround;
        // stall guard: give up this path if we stop getting closer for a long time
        if (d < lastD - 0.01) { lastD = d; stall = 0; } else if (++stall > 80) { path = null; goal = null; release(); }
    }

    /** Standing cell (feet) reachable from start: walk 1, step up 1, drop up to 3. */
    private List<int[]> astar(Sim sim, int sx, int sy, int sz) {
        class_2338 gp = null; int range = 0;
        if (goal instanceof GoalNear g) { gp = g.pos; range = g.range; }
        Map<Long, Integer> g = new HashMap<>();
        Map<Long, Long> from = new HashMap<>();
        PriorityQueue<long[]> open = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));
        long start = key(sx, sy, sz);
        g.put(start, 0);
        open.add(new long[]{0, start});
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < 40000) {
            long cur = open.poll()[1];
            int[] c = unkey(cur);
            if (goal.isInGoal(c[0], c[1], c[2])) {
                List<int[]> out = new ArrayList<>();
                long k = cur;
                while (k != start) { out.add(0, unkey(k)); k = from.get(k); }
                return out;
            }
            int gc = g.get(cur);
            for (int[] d : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int nx = c[0] + d[0], nz = c[2] + d[1];
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = c[1] + dy;
                    if (dy == 1 && !(clear(sim, c[0], c[1] + 2, c[2]))) continue;
                    if (!feetOk(sim, nx, ny, nz)) continue;
                    if (dy < 0 && !clearColumn(sim, nx, ny, nz, c[1])) continue;
                    long nk = key(nx, ny, nz);
                    int ng = gc + (dy == 0 ? 1 : (dy > 0 ? 2 : 1));
                    if (ng < g.getOrDefault(nk, Integer.MAX_VALUE)) {
                        g.put(nk, ng); from.put(nk, cur);
                        int h = (gp == null ? 0 : (int) (Math.abs(nx - gp.method_10263()) + Math.abs(nz - gp.method_10260())));
                        open.add(new long[]{ng + h, nk});
                    }
                    break;
                }
            }
        }
        return null;
    }

    private static boolean clear(Sim sim, int x, int y, int z) {
        class_2338 p = new class_2338(x, y, z);
        class_2680 s = sim.world.method_8320(p);
        return !Sim.solid(s);
    }

    /** Feet at (x,y,z): feet and head cells clear, ground below. */
    private static boolean feetOk(Sim sim, int x, int y, int z) {
        return clear(sim, x, y, z) && clear(sim, x, y + 1, z) && !clear(sim, x, y - 1, z);
    }

    private static boolean clearColumn(Sim sim, int x, int y, int z, int fromY) {
        for (int j = y + 1; j <= fromY + 1; j++) if (!clear(sim, x, j, z)) return false;
        return true;
    }

    private static long key(int x, int y, int z) { return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF); }
    private static int[] unkey(long k) { return new int[]{(int) (k >> 38), (int) (k << 52 >> 52), (int) (k << 26 >> 38)}; }
}
