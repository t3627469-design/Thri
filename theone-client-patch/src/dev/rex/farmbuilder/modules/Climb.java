package dev.rex.farmbuilder.modules;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1922;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_638;
import net.minecraft.class_746;

/**
 * "Build up": gets the player to blocks that are too high to reach.
 *
 * Creative: fly to a free spot within reach of the block.
 * Survival: when the spot to stand on is 2+ blocks above the player, build a pillar of spare
 * blocks next to the build (beside the platform the built layers form), climb it, step onto the
 * platform and keep building. The pillar stays up until the build is done (or stalls), then it is
 * mined down again. Works for only-build and human-builder.
 */
final class Climb {
    private static final int MAX_PILLAR = 38;
    private static final int STALL_TICKS = 1200;
    private static final int RETRY_TICKS = 300;

    private static final Pillar pillar = new Pillar();
    private static final Approach walker = new Approach();
    private static final Approach flier = new Approach();
    private static final Map<Class<?>, Map<String, Field>> fields = new HashMap<>();
    private static final Map<Class<?>, Method> holds = new HashMap<>();
    private static final Map<class_2338, Integer> failedUntil = new HashMap<>();

    private static int clock;
    private static int lastSize = -1;
    private static int lastProgress;
    private static int climbLevel = Integer.MIN_VALUE;
    private static class_2338 climbGoal;
    private static class_2338 climbTarget;
    private static class_1792 climbItem;
    private static Module climbModule;
    private static boolean flying;
    private static int flyTicks;
    private static class_2338 flyFor;
    private static class_2338 flySpot;
    private static class_2338 standFor;
    private static class_2338 standSpot;
    private static int standTick = -1000;
    private static long lastNag;

    private Climb() {
    }

    static void reset() {
        pillar.reset();
        walker.reset();
        flier.stop();
        climbLevel = Integer.MIN_VALUE;
        climbGoal = null;
        climbTarget = null;
        climbItem = null;
        climbModule = null;
        flying = false;
        flyFor = null;
        flySpot = null;
        standFor = null;
        standSpot = null;
        failedUntil.clear();
        lastSize = -1;
    }

    // ------------------------------------------------------------------ hooks

    /** Replaces the `null` approach in Reposition.tick so creative players reposition by flying. */
    static Approach flightApproach() {
        return Approach.canFly() ? flier : null;
    }

    /** True while the pillar is holding the player up and the climb logic owns the vertical state. */
    static boolean pillarReady(class_746 player) {
        return player.method_24828() && (climbLevel == Integer.MIN_VALUE || player.method_23318() >= climbLevel - 0.05);
    }

    /** Top of Module.tick(): pillar mine-down when the module leaves the building phase. */
    static boolean always(Module m) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            if (pillar.mode() != Pillar.Mode.OFF) {
                pillar.reset();
            }
            return false;
        }
        Pillar.Mode mode = pillar.mode();
        if (mode == Pillar.Mode.OFF) {
            return false;
        }
        if (mode == Pillar.Mode.HOLD) {
            String phase = phaseName(m);
            if (phase != null && !phase.equals("BUILD") && !phase.equals("HOTBAR")) {
                pillar.beginDown();
            }
            return false;
        }
        if (mode == Pillar.Mode.DOWN) {
            return !pillar.tickDown(walker);
        }
        return false;
    }

    /**
     * Start of every build tick (via Guard.pre). Runs the climb; returns true when it used the
     * tick so the normal build step is skipped.
     */
    static boolean tick(Planner planner, Module m) {
        ++clock;
        int size = planner.size();
        if (size != lastSize) {
            lastSize = size;
            lastProgress = clock;
        }
        Pillar.Mode mode = pillar.mode();
        if (mode == Pillar.Mode.WALK || mode == Pillar.Mode.UP) {
            return runClimb(planner, m, mode);
        }
        if (mode == Pillar.Mode.HOLD && (planner.isEmpty() || clock - lastProgress > STALL_TICKS)) {
            pillar.beginDown();
            return true;
        }
        return false;
    }

    /** Top of goToward(): fly (creative) or start a pillar (survival). True = handled this tick. */
    static boolean goToward(Module m, Planner planner, class_2338 target, boolean underUs) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null || underUs) {
            return false;
        }
        double reach = reach(m);
        class_243 eye = mc.field_1724.method_33571();
        double dx = target.method_10263() + 0.5 - eye.field_1352;
        double dy = target.method_10264() + 0.5 - eye.field_1351;
        double dz = target.method_10260() + 0.5 - eye.field_1350;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (Approach.canFly()) {
            return fly(target, dist, reach);
        }
        Pillar.Mode pm = pillar.mode();
        if (dist <= reach - 0.2 || pm == Pillar.Mode.WALK || pm == Pillar.Mode.UP || pm == Pillar.Mode.DOWN) {
            return false;
        }
        Integer wait = failedUntil.get(target);
        if (wait != null && wait > clock) {
            return false;
        }
        if (Approach.horizontal(target) > 10.0) {
            return false;
        }
        if (!target.equals(standFor) || clock - standTick > 20) {
            standFor = target;
            standTick = clock;
            standSpot = Stand.find(target, reach, planner);
        }
        double feet = mc.field_1724.method_23318();
        double level = standSpot != null ? (double) standSpot.method_10264() : (double) target.method_10264();
        if (level - feet < 1.9) {
            return false;
        }
        if (pm == Pillar.Mode.HOLD) {
            pillar.beginDown();
            return true;
        }
        return start(m, planner, target, standSpot, reach);
    }

    // ------------------------------------------------------------------ creative flight

    private static boolean fly(class_2338 target, double dist, double reach) {
        double near = Math.max(1.4, Math.min(3.0, reach - 0.6));
        if (dist <= near + 0.4) {
            if (flying) {
                flier.stop();
                flying = false;
            }
            flyTicks = 0;
            return false;
        }
        if (!target.equals(flyFor) || flySpot == null || !passableSpot(flySpot)) {
            flyFor = target;
            flySpot = chooseSpot(target, near);
            flyTicks = 0;
        }
        if (flySpot == null || ++flyTicks > 400) {
            if (flying) {
                flier.stop();
                flying = false;
            }
            return false;
        }
        flier.fly(flySpot, 0.8);
        flying = true;
        return true;
    }

    private static class_2338 chooseSpot(class_2338 t, double near) {
        class_310 mc = MeteorClient.mc;
        double px = mc.field_1724.method_23317();
        double py = mc.field_1724.method_23318();
        double pz = mc.field_1724.method_23321();
        class_2338 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dy = -1; dy <= 3; ++dy) {
                for (int dz = -3; dz <= 3; ++dz) {
                    class_2338 c = t.method_10069(dx, dy, dz);
                    if (c.equals(t) || c.method_10084().equals(t)) {
                        continue;
                    }
                    double ex = c.method_10263() + 0.5 - (t.method_10263() + 0.5);
                    double ey = c.method_10264() + 1.62 - (t.method_10264() + 0.5);
                    double ez = c.method_10260() + 0.5 - (t.method_10260() + 0.5);
                    double eye = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    if (eye < 1.4 || eye > near || !passableSpot(c)) {
                        continue;
                    }
                    double fx = c.method_10263() + 0.5 - px;
                    double fy = c.method_10264() - py;
                    double fz = c.method_10260() + 0.5 - pz;
                    double score = Math.sqrt(fx * fx + fy * fy + fz * fz);
                    if (Guard.isTarget(c) || Guard.isTarget(c.method_10084())) {
                        score += 6.0;
                    }
                    if (score < bestScore) {
                        bestScore = score;
                        best = c;
                    }
                }
            }
        }
        return best;
    }

    private static boolean passableSpot(class_2338 c) {
        class_638 w = MeteorClient.mc.field_1687;
        if (!w.method_8393(c.method_10263() >> 4, c.method_10260() >> 4)) {
            return false;
        }
        return passable(c) && passable(c.method_10084());
    }

    private static boolean passable(class_2338 p) {
        class_638 w = MeteorClient.mc.field_1687;
        class_2680 s = w.method_8320(p);
        return s.method_26215() || s.method_26227().method_15769() && s.method_26220((class_1922) w, p).method_1110();
    }

    // ------------------------------------------------------------------ survival pillar

    private static boolean start(Module m, Planner planner, class_2338 target, class_2338 stand, double reach) {
        class_1792 item = Pillar.pickItem(inventory(), planner.remainingItems().keySet());
        if (item == null) {
            nag(m, "Can't reach the block at %d %d %d from the ground and I have no spare blocks to build up with. Carry some cobblestone or dirt.", target);
            failedUntil.put(target, clock + RETRY_TICKS);
            return false;
        }
        class_2338 base = stand != null ? chooseBase(stand) : Pillar.pickColumn(target, reach, footprint());
        if (base == null) {
            nag(m, "No free spot beside the build to pillar up from to reach %d %d %d.", target);
            failedUntil.put(target, clock + RETRY_TICKS);
            return false;
        }
        int level = stand != null ? stand.method_10264() : target.method_10264();
        climbItem = item;
        climbModule = m;
        climbTarget = target;
        climbLevel = stand != null ? level : Integer.MIN_VALUE;
        climbGoal = stand != null ? new class_2338(base.method_10263(), level, base.method_10260()) : target;
        pillar.start(base, item, level);
        walker.reset();
        m.info("Building a pillar to reach height %d.", new Object[]{level});
        return true;
    }

    private static boolean runClimb(Planner planner, Module m, Pillar.Mode mode) {
        if (mode == Pillar.Mode.WALK) {
            pillar.tickWalk(walker);
        } else {
            final Module owner = climbModule != null ? climbModule : m;
            final class_1792 item = climbItem;
            pillar.tickUp(climbGoal, reach(owner), () -> hold(owner, item));
        }
        if (pillar.takeFailed()) {
            m.warning("The pillar didn't work out (no spare blocks, or I slid off). Coming down and resting that block.", new Object[0]);
            if (climbTarget != null) {
                planner.stuck(climbTarget);
                failedUntil.put(climbTarget, clock + RETRY_TICKS);
            }
        }
        if (pillar.mode() == Pillar.Mode.HOLD) {
            lastProgress = clock;
        }
        return true;
    }

    /**
     * Column next to the platform at the stand level. Walks outward over the standable platform
     * cells and returns the best free column beside it (nearest to the player, short pillar).
     */
    private static class_2338 chooseBase(class_2338 stand) {
        class_310 mc = MeteorClient.mc;
        class_638 w = mc.field_1687;
        int level = stand.method_10264();
        ArrayDeque<long[]> queue = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new long[]{stand.method_10263(), stand.method_10260()});
        seen.add(Pillar.key(stand.method_10263(), stand.method_10260()));
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<class_2338> bases = new ArrayList<>();
        while (!queue.isEmpty() && seen.size() < 2500 && bases.size() < 60) {
            long[] cur = queue.poll();
            for (int[] s : sides) {
                int nx = (int) cur[0] + s[0];
                int nz = (int) cur[1] + s[1];
                if (!seen.add(Pillar.key(nx, nz)) || !w.method_8393(nx >> 4, nz >> 4)) {
                    continue;
                }
                if (Stand.standable(new class_2338(nx, level, nz))) {
                    queue.add(new long[]{nx, nz});
                } else {
                    class_2338 base = column(nx, nz, level);
                    if (base != null) {
                        bases.add(base);
                    }
                }
            }
        }
        double px = mc.field_1724.method_23317();
        double pz = mc.field_1724.method_23321();
        class_2338 best = null;
        double bestScore = Double.MAX_VALUE;
        for (class_2338 b : bases) {
            double dx = b.method_10263() + 0.5 - px;
            double dz = b.method_10260() + 0.5 - pz;
            double score = Math.sqrt(dx * dx + dz * dz) + 2.0 * (level - b.method_10264());
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    /** Standing cell on the ground under a free column, or null when the column can't host a pillar. */
    private static class_2338 column(int x, int z, int level) {
        class_638 w = MeteorClient.mc.field_1687;
        if (!passable(new class_2338(x, level, z)) || !passable(new class_2338(x, level + 1, z))) {
            return null;
        }
        for (int y = level - 1; y >= level - MAX_PILLAR; --y) {
            class_2338 cell = new class_2338(x, y, z);
            if (passable(cell)) {
                continue;
            }
            class_2338 base = new class_2338(x, y + 1, z);
            if (level - base.method_10264() < 1 || !Stand.standable(base)) {
                return null;
            }
            for (int k = base.method_10264(); k <= level + 1; ++k) {
                if (Guard.isTarget(new class_2338(x, k, z))) {
                    return null;
                }
            }
            return base;
        }
        return null;
    }

    private static Set<Long> footprint() {
        Set<Long> set = new HashSet<>();
        for (class_2338 p : Guard.targetKeys()) {
            set.add(Pillar.key(p.method_10263(), p.method_10260()));
        }
        return set;
    }

    private static Map<class_1792, Integer> inventory() {
        Map<class_1792, Integer> held = new HashMap<>();
        class_1661 inv = MeteorClient.mc.field_1724.method_31548();
        for (int i = 0; i < 36; ++i) {
            class_1799 stack = inv.method_5438(i);
            if (stack.method_7960()) {
                continue;
            }
            held.merge(stack.method_7909(), stack.method_7947(), Integer::sum);
        }
        return held;
    }

    // ------------------------------------------------------------------ module access (reflection)

    private static Field field(Object o, String name) throws NoSuchFieldException {
        Map<String, Field> map = fields.computeIfAbsent(o.getClass(), k -> new HashMap<>());
        Field f = map.get(name);
        if (f == null) {
            f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            map.put(name, f);
        }
        return f;
    }

    private static double reach(Module m) {
        try {
            Object setting = field(m, "reach").get(m);
            Object v = setting.getClass().getMethod("get").invoke(setting);
            if (v instanceof Double) {
                return (Double) v;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // fall through
        }
        return 4.0;
    }

    private static String phaseName(Module m) {
        try {
            Object v = field(m, "phase").get(m);
            return v instanceof Enum ? ((Enum<?>) v).name() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean hold(Module m, class_1792 item) {
        try {
            Method h = holds.get(m.getClass());
            if (h == null) {
                h = m.getClass().getDeclaredMethod("hold", class_1792.class);
                h.setAccessible(true);
                holds.put(m.getClass(), h);
            }
            return (Boolean) h.invoke(m, item);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void nag(Module m, String fmt, class_2338 t) {
        long now = System.currentTimeMillis();
        if (now - lastNag > 20000L) {
            lastNag = now;
            m.warning(fmt, new Object[]{t.method_10263(), t.method_10264(), t.method_10260()});
        }
    }
}
