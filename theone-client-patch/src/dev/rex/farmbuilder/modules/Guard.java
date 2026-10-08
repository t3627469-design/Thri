package dev.rex.farmbuilder.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1297;
import net.minecraft.class_1922;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;

/**
 * Placement guard + instinct speed.
 *
 * - findPlacement: picks a support face / hit point that yields the wanted block state
 *   (log axis, stair/slab/trapdoor half, button/lever face) instead of "any face".
 * - pre: every build tick, looks for blocks that are the right block but the wrong state
 *   (e.g. a stair facing the wrong way), breaks them and puts them back in the planner.
 * - speedProfile: levels 1-5 as before, level 6 = instinct (no delay, snap aim).
 */
final class Guard {
    private static final int SCAN_RADIUS = 5;
    private static final int SCAN_EVERY = 3;
    private static final int SETTLE_TICKS = 8;
    private static final int MAX_FIXES = 2;
    private static final int FIX_TIMEOUT = 80;
    private static final int GIVE_UP_PER_BLOCK = 3;

    private static Map<class_2338, class_2680> targets;
    private static int clock;
    private static class_2338 fixPos;
    private static int fixStart;
    private static final Map<class_2338, Integer> firstSeen = new HashMap<>();
    private static final Map<class_2338, Integer> fixes = new HashMap<>();
    private static final Map<class_2338, Integer> cooldown = new HashMap<>();
    private static final Map<Object, Integer> giveUps = new HashMap<>();
    private static final Set<Object> unfixable = new HashSet<>();

    private Guard() {
    }

    static int[] speedProfile(int level) {
        switch (Math.max(1, Math.min(6, level))) {
            case 1:
                return new int[]{6, 10, 9, 1};
            case 2:
                return new int[]{4, 6, 14, 1};
            case 3:
                return new int[]{3, 5, 20, 1};
            case 4:
                return new int[]{2, 3, 30, 2};
            case 5:
                return new int[]{1, 2, 45, 3};
            default:
                return new int[]{0, 0, 90, 6};
        }
    }

    // ---------------------------------------------------------------- state compare

    private static boolean checked(String name) {
        return name.equals("facing") || name.equals("axis") || name.equals("half")
            || name.equals("type") || name.equals("face");
    }

    /** True when actual is the wanted block but one of the placement-decided properties differs. */
    static boolean wrongState(class_2680 actual, class_2680 want) {
        if (actual.method_26204() != want.method_26204()) {
            return false;
        }
        for (Object o : want.method_28501()) {
            class_2769 prop = (class_2769) o;
            String name = prop.method_11899();
            if (!checked(name) || !actual.method_28498(prop)) {
                continue;
            }
            Comparable w = want.method_11654(prop);
            Comparable a = actual.method_11654(prop);
            if (w.equals(a)) {
                continue;
            }
            String ws = String.valueOf(w);
            if (name.equals("half") || name.equals("type")) {
                if (!ws.equals("top") && !ws.equals("bottom")) {
                    continue;
                }
                String as = String.valueOf(a);
                if (!as.equals("top") && !as.equals("bottom")) {
                    continue;
                }
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- placement

    /**
     * Vertical hit offset (-0.25 bottom half, +0.25 top half, 0 none) that makes a click on the
     * given face produce the wanted state, or NaN when that face cannot produce it.
     */
    private static double adjust(class_2680 want, class_2350 side) {
        double dy = 0.0;
        for (Object o : want.method_28501()) {
            class_2769 prop = (class_2769) o;
            String name = prop.method_11899();
            Comparable v = want.method_11654(prop);
            String val = String.valueOf(v);
            switch (name) {
                case "axis":
                    if (!v.equals(side.method_10166())) {
                        return Double.NaN;
                    }
                    break;
                case "half":
                case "type": {
                    boolean top = val.equals("top");
                    boolean bottom = val.equals("bottom");
                    if (!top && !bottom) {
                        break;
                    }
                    int y = side.method_10164();
                    if (y > 0) {
                        if (top) {
                            return Double.NaN;
                        }
                    } else if (y < 0) {
                        if (bottom) {
                            return Double.NaN;
                        }
                    } else {
                        dy = top ? 0.25 : -0.25;
                    }
                    break;
                }
                case "face": {
                    int y = side.method_10164();
                    if (val.equals("floor") && y <= 0 || val.equals("ceiling") && y >= 0 || val.equals("wall") && y != 0) {
                        return Double.NaN;
                    }
                    break;
                }
                default:
                    break;
            }
        }
        return dy;
    }

    static Look.Placement findPlacement(class_2338 pos) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return null;
        }
        class_2680 want = targets == null ? null : targets.get(pos);
        class_243 eye = mc.field_1724.method_33571();
        Look.Placement best = null;
        double bestD = Double.MAX_VALUE;
        for (class_2350 dir : class_2350.values()) {
            class_2338 sup = pos.method_10093(dir);
            class_2680 s = mc.field_1687.method_8320(sup);
            if (s.method_26215() || s.method_45474() || !s.method_26227().method_15769()
                || s.method_26220((class_1922) mc.field_1687, sup).method_1110() || BlockUtils.isClickable(s.method_26204())) {
                continue;
            }
            class_2350 side = dir.method_10153();
            double dy = 0.0;
            if (want != null) {
                dy = adjust(want, side);
                if (Double.isNaN(dy)) {
                    continue;
                }
            }
            class_243 n = new class_243((double) side.method_10148(), (double) side.method_10164(), (double) side.method_10165());
            class_243 center = class_243.method_24953(sup);
            class_243 hit = center.method_1019(n.method_1021(0.5)).method_1031(0.0, dy, 0.0);
            double d = eye.method_1022(hit);
            if (d > 4.0 || d >= bestD || eye.method_1020(hit).method_1026(n) <= 0.0) {
                continue;
            }
            class_243 tgt = center.method_1019(n.method_1021(0.45)).method_1031(0.0, dy, 0.0);
            class_3965 r = mc.field_1687.method_17742(new class_3959(eye, tgt, class_3959.class_3960.field_17559,
                class_3959.class_242.field_1348, (class_1297) mc.field_1724));
            if (r == null || r.method_17783() != class_239.class_240.field_1332 || !r.method_17777().equals(sup)
                || r.method_17780() != side) {
                continue;
            }
            best = new Look.Placement(sup, side, hit);
            bestD = d;
        }
        return best;
    }

    // ---------------------------------------------------------------- fixer

    private static void reset(Map<class_2338, class_2680> wanted) {
        targets = wanted;
        clock = 0;
        fixPos = null;
        firstSeen.clear();
        fixes.clear();
        cooldown.clear();
        giveUps.clear();
        unfixable.clear();
    }

    /**
     * Called at the start of every build tick. Returns true when it used the tick (breaking a
     * wrong-state block), in which case the normal build step is skipped.
     */
    static boolean pre(Map<class_2338, class_2680> wanted, Planner planner, Module module) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1755 != null) {
            return false;
        }
        if (targets != wanted) {
            reset(wanted);
        }
        ++clock;
        if (fixPos != null) {
            return fix(planner, module);
        }
        if (clock % SCAN_EVERY != 0) {
            return false;
        }
        class_2338 c = mc.field_1724.method_24515();
        class_2338 best = null;
        double bestD = Double.MAX_VALUE;
        class_243 eye = mc.field_1724.method_33571();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; ++dx) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; ++dy) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; ++dz) {
                    class_2338 p = c.method_10069(dx, dy, dz);
                    class_2680 want = wanted.get(p);
                    if (want == null || unfixable.contains(want.method_26204())) {
                        continue;
                    }
                    if (!mc.field_1687.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) {
                        continue;
                    }
                    if (!wrongState(mc.field_1687.method_8320(p), want)) {
                        firstSeen.remove(p);
                        continue;
                    }
                    Integer since = firstSeen.get(p);
                    if (since == null) {
                        firstSeen.put(p, clock);
                        continue;
                    }
                    Integer cd = cooldown.get(p);
                    if (clock - since < SETTLE_TICKS || cd != null && cd > clock) {
                        continue;
                    }
                    double d = eye.method_1022(class_243.method_24953(p));
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        int done = fixes.getOrDefault(best, 0);
        class_2680 want = wanted.get(best);
        if (done >= MAX_FIXES) {
            firstSeen.remove(best);
            cooldown.put(best, Integer.MAX_VALUE);
            Object block = want.method_26204();
            int n = giveUps.merge(block, 1, Integer::sum);
            if (n == GIVE_UP_PER_BLOCK) {
                unfixable.add(block);
                module.warning("Can't get %s to come out in the right orientation from where I stand. Leaving those as they are.", new Object[]{block});
            }
            return false;
        }
        fixPos = best;
        fixStart = clock;
        return fix(planner, module);
    }

    private static boolean fix(Planner planner, Module module) {
        class_310 mc = MeteorClient.mc;
        class_2338 p = fixPos;
        class_2680 want = targets.get(p);
        class_2680 now = mc.field_1687.method_8320(p);
        if (want == null || now.method_26204() != want.method_26204() && !now.method_45474()) {
            fixPos = null;
            Look.stopMining();
            return false;
        }
        if (now.method_45474()) {
            Look.stopMining();
            fixes.merge(p, 1, Integer::sum);
            firstSeen.remove(p);
            if (!planner.pending(p)) {
                planner.add(p);
            }
            planner.placed(p);
            fixPos = null;
            return false;
        }
        if (!wrongState(now, want)) {
            Look.stopMining();
            fixPos = null;
            return false;
        }
        if (clock - fixStart > FIX_TIMEOUT) {
            Look.stopMining();
            fixes.merge(p, MAX_FIXES, Integer::sum);
            fixPos = null;
            return false;
        }
        class_243 aim = Look.breakAim(p);
        if (aim == null) {
            Look.stopMining();
            cooldown.put(p, clock + 100);
            fixPos = null;
            return false;
        }
        boolean on = Look.lookAt(aim, 90.0f);
        for (int i = 1; i < 6 && !on; ++i) {
            on = Look.lookAt(aim, 90.0f);
        }
        if (on && Look.crosshairOn(p)) {
            Look.attackCrosshairBlock();
        }
        return true;
    }
}
