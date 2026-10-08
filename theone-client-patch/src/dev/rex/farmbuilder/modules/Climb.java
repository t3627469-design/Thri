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
 * Getting the player to blocks it can't place from where it stands.
 *
 * No flying, in survival or creative (flight is switched off when the builder has to move).
 * When the spot to stand on is 2+ blocks above the player, builds a pillar of spare
 * blocks beside the platform the finished layers form, climbs it, steps onto the platform and keeps
 * building from up there. The pillar is mined down when the build is done, stalls, or the module
 * leaves its build phase. Works for only-build and human-builder.
 */
final class Climb {
    private static final int MAX_PILLAR = 38;
    private static final int STALL_TICKS = 1200;
    private static final int RETRY_TICKS = 300;

    private static final Pillar pillar = new Pillar();
    private static final Approach walker = new Approach();
    private static final Map<Class<?>, Map<String, Field>> fields = new HashMap<>();
    private static final Map<Class<?>, Method> holds = new HashMap<>();
    private static final Map<class_2338, Integer> failedUntil = new HashMap<>();

    private static int clock;
    private static int lastSize = -1;
    private static int lastProgress;
    private static int lastStallNote = -100000;
    private static int climbLevel = Integer.MIN_VALUE;
    private static class_2338 climbGoal;
    private static class_2338 climbTarget;
    private static class_1792 climbItem;
    private static Module climbModule;
    private static class_2338 standFor;
    private static class_2338 standSpot;
    private static int standTick = -1000;
    private static long lastNag;

    private Climb() {
    }

    static void reset() {
        pillar.reset();
        walker.reset();
        climbLevel = Integer.MIN_VALUE;
        climbGoal = null;
        climbTarget = null;
        climbItem = null;
        climbModule = null;
        standFor = null;
        standSpot = null;
        failedUntil.clear();
        lastSize = -1;
    }

    // ================================================================== hooks

    /** Pillar.tickUp's "on the ground" test: also wait until the feet reach the wanted height. */
    static boolean pillarReady(class_746 player) {
        return player.method_24828() && (climbLevel == Integer.MIN_VALUE || player.method_23318() >= climbLevel - 0.05);
    }

    /** Top of Module.tick(): mine the pillar down (and consume the tick) whenever it is coming down. */
    static boolean always(Module m) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            if (pillar.mode() != Pillar.Mode.OFF) {
                pillar.reset();
            }
            return false;
        }
        Pillar.Mode mode = pillar.mode();
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

    /** Start of every build tick. True = the climb used this tick. */
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
        if (mode == Pillar.Mode.HOLD) {
            if (planner.isEmpty()) {
                m.info("Build finished. Taking the pillar down.", new Object[0]);
                pillar.beginDown();
                return true;
            }
            if (clock - lastProgress > STALL_TICKS && clock - lastStallNote > STALL_TICKS) {
                lastStallNote = clock;
                m.warning("Up on the build but nothing here is reachable right now (%d blocks left). Keeping the pillar up; it won't come down until the build is done.", new Object[]{planner.size()});
            }
        }
        return false;
    }

    /**
     * The builder wants to place target but can't from where it stands. Decide how to get there:
     * place from here if a correctly oriented face is visible; otherwise walk to the nearest standing
     * spot that works; only when no spot is reachable on foot, build a pillar up to one. True = this
     * tick is used for moving.
     */
    static boolean goToward(Module m, Planner planner, class_2338 target, boolean underUs) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return false;
        }
        double reach = reach(m);
        landIfFlying();
        Pillar.Mode pm = pillar.mode();
        if (underUs || pm == Pillar.Mode.WALK || pm == Pillar.Mode.UP || pm == Pillar.Mode.DOWN) {
            return false;
        }
        if (Look.findPlacement(target) != null) {
            walker.stop();
            return false;
        }
        Integer wait = failedUntil.get(target);
        if (wait != null && wait > clock) {
            walker.stop();
            return false;
        }
        if (!target.equals(standFor) || clock - standTick > 20) {
            standFor = target;
            standTick = clock;
            standSpot = Stand.find(target, reach, planner);
        }
        class_746 p = mc.field_1724;
        double feet = p.method_23318();
        if (standSpot != null && level(standSpot) - feet < 1.9) {
            class_2338 here = p.method_24515();
            if (here.equals(standSpot)) {
                walker.stop();
                return false;
            }
            walker.walk(standSpot, 0);
            return true;
        }
        walker.stop();
        double level = standSpot != null ? level(standSpot) : (double) target.method_10264();
        if (level - feet < 1.9 || pm != Pillar.Mode.OFF) {
            return false;
        }
        if (Approach.horizontal(target) > 10.0) {
            return false;
        }
        return start(m, planner, target, standSpot, reach);
    }

    private static double level(class_2338 spot) {
        return spot.method_10264();
    }

    // ================================================================== survival pillar

    private static boolean start(Module m, Planner planner, class_2338 target, class_2338 stand, double reach) {
        class_1792 item = Approach.canFly() ? Pillar.creativeItem() : Pillar.pickItem(inventory(), planner.remainingItems().keySet());
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
        landIfFlying();
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
     * Column next to the platform at the stand level: walks outward over the standable platform
     * cells and returns the best free column beside it (close to the player, short pillar).
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
        if (!passable(new class_2338(x, level, z)) || !passable(new class_2338(x, level + 1, z))) {
            return null;
        }
        for (int y = level - 1; y >= level - MAX_PILLAR; --y) {
            if (passable(new class_2338(x, y, z))) {
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

    /** No flying: creative flight is switched off whenever the builder moves or climbs. */
    private static void landIfFlying() {
        class_746 p = MeteorClient.mc.field_1724;
        if (p != null && p.method_31549().field_7479) {
            p.method_31549().field_7479 = false;
        }
    }

    /** Air or a non-colliding block (a cell a player can stand in). */
    private static boolean passable(class_2338 p) {
        class_638 w = MeteorClient.mc.field_1687;
        class_2680 s = w.method_8320(p);
        return s.method_26215() || s.method_26227().method_15769() && s.method_26220((class_1922) w, p).method_1110();
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

    // ================================================================== module access (reflection)

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

    /** True while a pillar is going up or coming down (the printer leaves the bot alone then). */
    static boolean pillarBusy() {
        Pillar.Mode m = pillar.mode();
        return m == Pillar.Mode.WALK || m == Pillar.Mode.UP || m == Pillar.Mode.DOWN;
    }

    /** An int setting of a module (its private Setting field's get()), or the fallback. */
    static int intSetting(Module m, String field, int fallback) {
        try {
            Object v = field(m, field).get(m);
            Object value = v.getClass().getMethod("get").invoke(v);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    static double reach(Module m) {
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
