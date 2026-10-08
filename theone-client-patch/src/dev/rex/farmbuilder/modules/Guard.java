package dev.rex.farmbuilder.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1747;
import net.minecraft.class_1750;
import net.minecraft.class_1792;
import net.minecraft.class_1838;
import net.minecraft.class_1922;
import net.minecraft.class_2248;
import net.minecraft.class_239;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_310;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_746;

/**
 * Placement brain + hook entry points.
 *
 * Placement: for a schematic block, every visible support face and several hit points on it are
 * tried; for each one Minecraft's own Block.getPlacementState runs with the rotation the player
 * will have when aiming there, so the resulting state is known before clicking. Only clicks that
 * produce the schematic's orientation (facing, axis, half, slab type, face, rotation, hinge, ...)
 * are chosen, and the click itself is gated on the same prediction with the exact rotation and
 * hit the server will see. If a block can't be oriented right from anywhere for a while, it is
 * placed anyway (best effort) so the build never deadlocks.
 */
final class Guard {
    private static final double HIT_REACH = 4.0;
    private static final double[][] OFFSETS = {{0, 0}, {0.3, 0}, {-0.3, 0}, {0, 0.3}, {0, -0.3}};
    private static final float MARGIN = 4.0f;
    private static final int FALLBACK_TICKS = 200;
    private static final int GATE_FALLBACK = 60;

    private static Map<class_2338, class_2680> targets;
    private static int clock;
    private static float sentYaw;
    private static float sentPitch;
    private static boolean sentKnown;
    private static final Map<class_2338, Integer> orientBlocked = new HashMap<>();
    private static final Map<class_2338, Integer> gateDenied = new HashMap<>();
    private static final Map<class_2338, Boolean> fallback = new HashMap<>();
    private static final Map<class_2680, Boolean> orientableCache = new HashMap<>();
    private static int errors;

    private Guard() {
    }

    // ================================================================== speed

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

    // ================================================================== hooks

    /** Top of tickBuild(). Returns true when the tick was used (climbing), so the build step is skipped. */
    static boolean pre(Map<class_2338, class_2680> wanted, Planner planner, Module module) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return false;
        }
        sentYaw = mc.field_1724.method_36454();
        sentPitch = mc.field_1724.method_36455();
        sentKnown = true;
        if (targets != wanted) {
            reset(wanted);
        }
        ++clock;
        if (clock % 100 == 0) {
            prune(planner);
        }
        if (mc.field_1755 != null || disabled()) {
            return false;
        }
        try {
            return Climb.tick(planner, module);
        } catch (Throwable t) {
            error(t);
            return false;
        }
    }

    static boolean goToward(Module m, Planner planner, class_2338 target, boolean underUs) {
        if (disabled()) {
            return false;
        }
        try {
            return Climb.goToward(m, planner, target, underUs);
        } catch (Throwable t) {
            error(t);
            return false;
        }
    }

    static boolean always(Module m) {
        if (disabled()) {
            return false;
        }
        try {
            return Climb.always(m);
        } catch (Throwable t) {
            error(t);
            return false;
        }
    }

    static boolean pillarReady(class_746 player) {
        return Climb.pillarReady(player);
    }

    /** Replaces Look.findPlacement. */
    static Look.Placement findPlacement(class_2338 pos) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return null;
        }
        class_243 eye = mc.field_1724.method_33571();
        class_2680 want = targets == null ? null : targets.get(pos);
        if (want == null || disabled()) {
            return nearestFace(pos, eye);
        }
        try {
            boolean loose = Boolean.TRUE.equals(fallback.get(pos));
            Look.Placement p = best(pos, want, eye, !loose);
            if (p != null || loose) {
                return p;
            }
            if (best(pos, want, eye, false) == null) {
                return null;
            }
            Integer since = orientBlocked.get(pos);
            if (since == null) {
                orientBlocked.put(pos, clock);
            } else if (clock - since > FALLBACK_TICKS) {
                fallback.put(pos, Boolean.TRUE);
                return best(pos, want, eye, false);
            }
            return null;
        } catch (Throwable t) {
            error(t);
            return nearestFace(pos, eye);
        }
    }

    /** True when this block can only be oriented right from somewhere else (not just out of sight). */
    static boolean needsOtherSpot(class_2338 pos) {
        return orientBlocked.containsKey(pos) && !Boolean.TRUE.equals(fallback.get(pos));
    }

    static boolean fellBack(class_2338 pos) {
        return Boolean.TRUE.equals(fallback.get(pos));
    }

    /** Start of Look.useCrosshairBlock(): false = don't click this tick (it would come out wrong). */
    static boolean allowClick() {
        if (disabled() || targets == null || !sentKnown) {
            return true;
        }
        try {
            class_310 mc = MeteorClient.mc;
            class_239 h = mc.field_1765;
            if (mc.field_1724 == null || mc.field_1687 == null || !(h instanceof class_3965)) {
                return true;
            }
            class_3965 hit = (class_3965) h;
            if (hit.method_17783() != class_239.class_240.field_1332) {
                return true;
            }
            class_2338 sup = hit.method_17777();
            class_2338 pos = mc.field_1687.method_8320(sup).method_45474() ? sup : sup.method_10093(hit.method_17780());
            class_2680 want = targets.get(pos);
            if (want == null || Boolean.TRUE.equals(fallback.get(pos)) || !orientable(want)) {
                return true;
            }
            if (variantOk(want, hit.method_17780()) && matches(predict(want, hit, sentYaw, sentPitch), want)) {
                gateDenied.remove(pos);
                return true;
            }
            int n = gateDenied.merge(pos, 1, Integer::sum);
            if (n > GATE_FALLBACK) {
                fallback.put(pos, Boolean.TRUE);
                return true;
            }
            return false;
        } catch (Throwable t) {
            error(t);
            return true;
        }
    }

    // ================================================================== placement search

    /** The original rule: closest visible face center, no orientation. */
    static Look.Placement nearestFace(class_2338 pos, class_243 eye) {
        Look.Placement best = null;
        double bestD = Double.MAX_VALUE;
        for (class_2350 dir : class_2350.values()) {
            class_2338 sup = pos.method_10093(dir);
            if (!support(sup)) {
                continue;
            }
            class_2350 side = dir.method_10153();
            class_243 n = normal(side);
            class_243 center = class_243.method_24953(sup);
            class_243 hit = center.method_1019(n.method_1021(0.5));
            double d = eye.method_1022(hit);
            if (d > HIT_REACH || d >= bestD || eye.method_1020(hit).method_1026(n) <= 0.0) {
                continue;
            }
            if (!visible(eye, center.method_1019(n.method_1021(0.45)), sup, side)) {
                continue;
            }
            best = new Look.Placement(sup, side, hit);
            bestD = d;
        }
        return best;
    }

    /**
     * Best placement for a schematic block from a given eye position. strict = only placements whose
     * predicted state has the schematic orientation; otherwise the closest visible one.
     */
    static Look.Placement best(class_2338 pos, class_2680 want, class_243 eye, boolean strict) {
        if (!orientable(want)) {
            return nearestFace(pos, eye);
        }
        List<Object[]> cands = new ArrayList<>();
        for (class_2350 dir : class_2350.values()) {
            class_2338 sup = pos.method_10093(dir);
            if (!support(sup)) {
                continue;
            }
            class_2350 side = dir.method_10153();
            if (strict && !variantOk(want, side)) {
                continue;
            }
            class_243 n = normal(side);
            class_243 center = class_243.method_24953(sup);
            for (double[] o : OFFSETS) {
                class_243 off = tangent(side, o[0], o[1]);
                class_243 hit = center.method_1019(n.method_1021(0.5)).method_1019(off);
                double d = eye.method_1022(hit);
                if (d > HIT_REACH || eye.method_1020(hit).method_1026(n) <= 0.0) {
                    continue;
                }
                cands.add(new Object[]{d, sup, side, hit, center.method_1019(n.method_1021(0.45)).method_1019(off)});
            }
        }
        cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        Look.Placement matched = null;
        for (Object[] c : cands) {
            class_2338 sup = (class_2338) c[1];
            class_2350 side = (class_2350) c[2];
            class_243 hit = (class_243) c[3];
            if (!visible(eye, (class_243) c[4], sup, side)) {
                continue;
            }
            if (!strict) {
                return new Look.Placement(sup, side, hit);
            }
            float[] rot = angles(eye, hit);
            class_3965 bhr = new class_3965(hit, side, sup, false);
            if (!matches(predict(want, bhr, rot[0], rot[1]), want)) {
                continue;
            }
            Look.Placement p = new Look.Placement(sup, side, hit);
            if (robust(want, bhr, rot)) {
                return p;
            }
            if (matched == null) {
                matched = p;
            }
        }
        return matched;
    }

    private static boolean robust(class_2680 want, class_3965 bhr, float[] rot) {
        return matches(predict(want, bhr, rot[0] + MARGIN, rot[1]), want)
            && matches(predict(want, bhr, rot[0] - MARGIN, rot[1]), want)
            && matches(predict(want, bhr, rot[0], clampPitch(rot[1] + MARGIN)), want)
            && matches(predict(want, bhr, rot[0], clampPitch(rot[1] - MARGIN)), want);
    }

    private static boolean support(class_2338 sup) {
        class_310 mc = MeteorClient.mc;
        class_2680 s = mc.field_1687.method_8320(sup);
        return !(s.method_26215() || s.method_45474() || !s.method_26227().method_15769()
            || s.method_26220((class_1922) mc.field_1687, sup).method_1110() || BlockUtils.isClickable(s.method_26204()));
    }

    private static boolean visible(class_243 eye, class_243 target, class_2338 sup, class_2350 side) {
        class_310 mc = MeteorClient.mc;
        class_3965 r = mc.field_1687.method_17742(new class_3959(eye, target, class_3959.class_3960.field_17559,
            class_3959.class_242.field_1348, (class_1297) mc.field_1724));
        return r != null && r.method_17783() == class_239.class_240.field_1332 && r.method_17777().equals(sup) && r.method_17780() == side;
    }

    private static class_243 normal(class_2350 side) {
        return new class_243((double) side.method_10148(), (double) side.method_10164(), (double) side.method_10165());
    }

    /** Offset in the plane of a face: (a, b) along the two axes perpendicular to the face normal. */
    private static class_243 tangent(class_2350 side, double a, double b) {
        if (side.method_10148() != 0) {
            return new class_243(0.0, a, b);
        }
        if (side.method_10164() != 0) {
            return new class_243(a, 0.0, b);
        }
        return new class_243(b, a, 0.0);
    }

    static float[] angles(class_243 eye, class_243 t) {
        double dx = t.field_1352 - eye.field_1352;
        double dy = t.field_1351 - eye.field_1351;
        double dz = t.field_1350 - eye.field_1350;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = wrap((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float pitch = clampPitch((float) -Math.toDegrees(Math.atan2(dy, h)));
        return new float[]{yaw, pitch};
    }

    private static float wrap(float f) {
        float r = f % 360.0f;
        if (r >= 180.0f) {
            r -= 360.0f;
        }
        if (r < -180.0f) {
            r += 360.0f;
        }
        return r;
    }

    private static float clampPitch(float p) {
        return Math.max(-90.0f, Math.min(90.0f, p));
    }

    // ================================================================== prediction

    /** Runs the block's own placement logic for this click with the given rotation. */
    static class_2680 predict(class_2680 want, class_3965 hit, float yaw, float pitch) {
        class_746 p = MeteorClient.mc.field_1724;
        float oy = p.method_36454();
        float op = p.method_36455();
        try {
            p.method_36456(yaw);
            p.method_36457(pitch);
            class_1750 ctx = new class_1750(new class_1838(p, class_1268.field_5808, hit));
            return want.method_26204().method_9605(ctx);
        } catch (Throwable t) {
            return null;
        } finally {
            p.method_36456(oy);
            p.method_36457(op);
        }
    }

    private static boolean checked(String name, String value) {
        switch (name) {
            case "facing":
            case "axis":
            case "face":
            case "rotation":
            case "hinge":
            case "attachment":
            case "orientation":
                return true;
            case "half":
            case "type":
                return value.equals("top") || value.equals("bottom");
            default:
                return false;
        }
    }

    /** True when the schematic state has something the click decides (or is a wall/floor variant). */
    static boolean orientable(class_2680 want) {
        Boolean known = orientableCache.get(want);
        if (known == null) {
            known = computeOrientable(want);
            orientableCache.put(want, known);
        }
        return known;
    }

    private static boolean computeOrientable(class_2680 want) {
        if (variantRule(want) != 0) {
            return true;
        }
        for (Object o : want.method_28501()) {
            class_2769 prop = (class_2769) o;
            if (checked(prop.method_11899(), String.valueOf(want.method_11654(prop)))) {
                return true;
            }
        }
        return false;
    }

    static boolean matches(class_2680 pred, class_2680 want) {
        if (pred == null || pred.method_26204() != want.method_26204()) {
            return false;
        }
        for (Object o : want.method_28501()) {
            class_2769 prop = (class_2769) o;
            Comparable w = want.method_11654(prop);
            if (!checked(prop.method_11899(), String.valueOf(w))) {
                continue;
            }
            if (!pred.method_28498(prop) || !w.equals(pred.method_11654(prop))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Torches, signs, banners, skulls and coral fans come from one item that places a wall or a
     * floor block depending on the clicked face. 1 = wall variant (side face), 2 = floor variant
     * (top face), 3 = hanging sign (bottom face), 0 = no rule.
     */
    private static int variantRule(class_2680 want) {
        class_2248 block = want.method_26204();
        class_1792 item = block.method_8389();
        if (item instanceof class_1747 && ((class_1747) item).method_7711() != block) {
            return 1;
        }
        String id = String.valueOf(block);
        if (id.contains("hanging_sign")) {
            return 3;
        }
        if (id.endsWith("torch}") || id.endsWith("_sign}") || id.endsWith("_banner}") || id.endsWith("_skull}")
            || id.endsWith("_head}") || id.endsWith("coral_fan}")) {
            return 2;
        }
        return 0;
    }

    private static boolean variantOk(class_2680 want, class_2350 side) {
        int rule = variantRule(want);
        int y = side.method_10164();
        return rule == 0 || rule == 1 && y == 0 || rule == 2 && y > 0 || rule == 3 && y < 0;
    }

    // ================================================================== bookkeeping

    static boolean isTarget(class_2338 p) {
        return targets != null && targets.containsKey(p);
    }

    static class_2680 target(class_2338 p) {
        return targets == null ? null : targets.get(p);
    }

    static Iterable<class_2338> targetKeys() {
        return targets == null ? List.<class_2338>of() : targets.keySet();
    }

    private static void reset(Map<class_2338, class_2680> wanted) {
        targets = wanted;
        clock = 0;
        orientBlocked.clear();
        gateDenied.clear();
        fallback.clear();
        Climb.reset();
    }

    private static void prune(Planner planner) {
        for (Map<class_2338, ?> m : List.<Map<class_2338, ?>>of(orientBlocked, gateDenied, fallback)) {
            Iterator<class_2338> it = m.keySet().iterator();
            while (it.hasNext()) {
                if (!planner.pending(it.next())) {
                    it.remove();
                }
            }
        }
    }

    static boolean disabled() {
        return errors >= 3;
    }

    static void error(Throwable t) {
        if (++errors <= 3) {
            System.out.println("[TheOne guard] " + t);
            t.printStackTrace();
        }
    }
}
