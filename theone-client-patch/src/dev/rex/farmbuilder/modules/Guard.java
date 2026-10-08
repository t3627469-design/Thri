package dev.rex.farmbuilder.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.class_638;
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
    /** Ticks a block may stay unplaceable-with-the-right-facing before it is dropped and reported. */
    private static final int GIVE_UP_TICKS = 400;
    private static final int GATE_LIMIT = 300;
    /** No placement for this many ticks while blocks remain: leave what is left, named in a warning. */
    private static final int STALL_LIMIT = 1500;

    private static Map<class_2338, class_2680> targets;
    private static int clock;
    private static float sentYaw;
    private static float sentPitch;
    private static boolean sentKnown;
    private static final Map<class_2338, Integer> orientBlocked = new HashMap<>();
    private static final Map<class_2338, Integer> gateDenied = new HashMap<>();
    private static final Set<class_2338> skipped = new HashSet<>();
    private static Planner curPlanner;
    private static Module curModule;
    private static int lastSize = -1;
    private static int lastProgress;
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
        curPlanner = planner;
        curModule = module;
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
        if (planner.size() != lastSize) {
            lastSize = planner.size();
            lastProgress = clock;
        } else if (clock - lastProgress > STALL_LIMIT && planner.size() > 0) {
            stalled(planner, module);
            lastProgress = clock;
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

    /**
     * Replaces Look.findPlacement. Oriented blocks get only placements that produce the schematic
     * state; if none is visible from here the block is left for a standing spot that has one.
     * A block that never gets one within GIVE_UP_TICKS is dropped and reported, never placed wrong.
     */
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
            if (!orientable(want)) {
                return best(pos, want, eye, false);
            }
            Look.Placement p = best(pos, want, eye, true);
            if (p != null) {
                orientBlocked.remove(pos);
                return p;
            }
            if (best(pos, want, eye, false) == null) {
                return null;
            }
            Integer since = orientBlocked.get(pos);
            if (since == null) {
                orientBlocked.put(pos, clock);
            } else if (clock - since > GIVE_UP_TICKS) {
                giveUp(pos);
            }
            return null;
        } catch (Throwable t) {
            error(t);
            return nearestFace(pos, eye);
        }
    }

    /** Drops a block that can't be placed facing right from anywhere reachable, and says so. */
    private static void giveUp(class_2338 pos) {
        orientBlocked.remove(pos);
        gateDenied.remove(pos);
        if (curPlanner != null) {
            curPlanner.drop(pos);
        }
        skipped.add(pos);
        bumpSkipped(curModule, 1);
        if (curModule != null) {
            curModule.warning("Skipped %s: I can't place it facing the right way from anywhere I can reach. Place it by hand.",
                new Object[]{pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260()});
        }
    }

    /** Adds to the module's own skipped count so its "Done: N placed, M skipped" line stays true. */
    private static void bumpSkipped(Module module, int n) {
        if (module == null || n <= 0) {
            return;
        }
        try {
            java.lang.reflect.Field f = module.getClass().getDeclaredField("skipped");
            f.setAccessible(true);
            f.setInt(module, f.getInt(module) + n);
        } catch (ReflectiveOperationException | RuntimeException e) {
            error(e);
        }
    }

    /** Nothing placed for STALL_LIMIT ticks: drop what is left, and name the first few. */
    private static void stalled(Planner planner, Module module) {
        List<class_2338> left = new ArrayList<>();
        for (class_2338 p : targets.keySet()) {
            if (planner.pending(p)) {
                left.add(p);
            }
        }
        for (class_2338 p : left) {
            planner.drop(p);
            skipped.add(p);
        }
        bumpSkipped(module, left.size());
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < Math.min(8, left.size()); i++) {
            class_2338 p = left.get(i);
            names.append(names.length() > 0 ? ", " : "").append(p.method_10263()).append(' ').append(p.method_10264()).append(' ').append(p.method_10260());
        }
        module.warning("Stopped waiting: %d block(s) I can't reach or place facing the right way from here (%s%s). Place those by hand.",
            new Object[]{left.size(), names.toString(), left.size() > 8 ? ", ..." : ""});
    }

    /** Blocks dropped because no correctly oriented placement was reachable. */
    static Set<class_2338> skipped() {
        return skipped;
    }

    /**
     * Where to stand to place pos: the nearest standing cell (feet) from which a placement exists
     * with the schematic's orientation (or any visible placement when there is none), searched
     * within reach. Null when no cell can place it. Replaces Stand.find, which only looked at distance
     * and so picked spots that could not see any face to click.
     */
    static class_2338 standFor(class_2338 pos, double reach, Planner planner) {
        class_310 mc = MeteorClient.mc;
        if (mc.field_1724 == null || mc.field_1687 == null) {
            return null;
        }
        class_2680 want = targets == null ? null : targets.get(pos);
        double radius = Math.max(2.0, reach - 0.5);
        double r2 = radius * radius;
        double px = mc.field_1724.method_23317(), py = mc.field_1724.method_23318(), pz = mc.field_1724.method_23321();
        List<Object[]> cands = new ArrayList<>();
        int bx = pos.method_10263(), by = pos.method_10264(), bz = pos.method_10260();
        for (int i = bx - 4; i <= bx + 4; i++) {
            for (int j = bz - 4; j <= bz + 4; j++) {
                if (!mc.field_1687.method_8393(i >> 4, j >> 4)) {
                    continue;
                }
                for (int k = by - 4; k <= by; k++) {
                    double dx = i + 0.5 - (bx + 0.5), dy = k + 1.62 - (by + 0.5), dz = j + 0.5 - (bz + 0.5);
                    if (dx * dx + dy * dy + dz * dz > r2 + 0.5) {
                        continue;
                    }
                    if (i == bx && j == bz && (k == by || k == by - 1)) {
                        continue;
                    }
                    class_2338 c = new class_2338(i, k, j);
                    if (!Stand.standable(c)) {
                        continue;
                    }
                    double hx = i + 0.5 - px, hz = j + 0.5 - pz;
                    double score = Math.sqrt(hx * hx + hz * hz) + 3.0 * Math.abs(k - py);
                    if (planner.pending(c)) {
                        score += 3.0;
                    }
                    if (planner.pending(c.method_10084())) {
                        score += 3.0;
                    }
                    cands.add(new Object[]{score, c});
                }
            }
        }
        cands.sort((a, b) -> Double.compare((Double) a[0], (Double) b[0]));
        int checked = 0;
        for (Object[] c : cands) {
            if (++checked > 60) {
                break;
            }
            class_2338 cell = (class_2338) c[1];
            if (want == null || !orientable(want)) {
                if (best(pos, want, cellEye(cell, 0.5, 0.5), false) != null) {
                    return cell;
                }
            } else if (strictEverywhere(pos, want, cell)) {
                return cell;
            }
        }
        return null;
    }

    private static class_243 cellEye(class_2338 cell, double dx, double dz) {
        return new class_243(cell.method_10263() + dx, cell.method_10264() + 1.62, cell.method_10260() + dz);
    }

    /**
     * A standing cell counts only if the correctly oriented placement exists from a 3x3 grid of points
     * inside it: the player never stops exactly on the centre, and a few tenths of a block changes what
     * is visible.
     */
    private static boolean strictEverywhere(class_2338 pos, class_2680 want, class_2338 cell) {
        double[][] samples = new double[9][];
        int n = 0;
        for (double x : new double[]{0.15, 0.5, 0.85}) {
            for (double z : new double[]{0.15, 0.5, 0.85}) {
                samples[n++] = new double[]{x, z};
            }
        }
        for (double[] s : samples) {
            if (best(pos, want, cellEye(cell, s[0], s[1]), true) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Start of Look.useCrosshairBlock() and Look.attackCrosshairBlock(): false = don't click this tick.
     * The crosshair the game clicks with is the one from before this tick's turn, but the server
     * takes the new rotation (facing) with it, so a click on a tick that turned gives the wrong
     * state. Turn now, click next tick.
     */
    static boolean allowClick() {
        if (disabled() || targets == null || !sentKnown) {
            return true;
        }
        try {
            class_310 mc = MeteorClient.mc;
            class_746 p = mc.field_1724;
            if (p == null) {
                return true;
            }
            if (Math.abs(p.method_36454() - sentYaw) > 1.0e-4f || Math.abs(p.method_36455() - sentPitch) > 1.0e-4f) {
                return false;
            }
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
            if (want == null || !orientable(want)) {
                return true;
            }
            if (variantOk(want, hit.method_17780()) && matches(predict(want, hit, sentYaw, sentPitch), want)) {
                gateDenied.remove(pos);
                return true;
            }
            if (gateDenied.merge(pos, 1, Integer::sum) > GATE_LIMIT) {
                giveUp(pos);
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
        skipped.clear();
        Climb.reset();
    }

    private static void prune(Planner planner) {
        for (Map<class_2338, ?> m : List.<Map<class_2338, ?>>of(orientBlocked, gateDenied)) {
            Iterator<class_2338> it = m.keySet().iterator();
            while (it.hasNext()) {
                if (!planner.pending(it.next())) {
                    it.remove();
                }
            }
        }
    }

    /**
     * Manual walking: true when the player should press forward. Walks onto a free cell with
     * ground under it, or into a one-block step (Approach jumps when it gets stuck there). Never
     * walks off an edge. The original version returned true only when a block was ahead.
     */
    static boolean groundToward(float yaw) {
        class_310 mc = MeteorClient.mc;
        class_746 p = mc.field_1724;
        if (p == null || mc.field_1687 == null) {
            return false;
        }
        double rad = Math.toRadians(yaw);
        int fx = (int) Math.floor(p.method_23317() - Math.sin(rad) * 0.8);
        int fz = (int) Math.floor(p.method_23321() + Math.cos(rad) * 0.8);
        int fy = (int) Math.floor(p.method_23318() + 0.01);
        boolean feetFree = clear(fx, fy, fz);
        boolean headFree = clear(fx, fy + 1, fz);
        if (feetFree) {
            return headFree && !clear(fx, fy - 1, fz);
        }
        return headFree;
    }

    private static boolean clear(int x, int y, int z) {
        class_638 w = MeteorClient.mc.field_1687;
        class_2338 pos = new class_2338(x, y, z);
        class_2680 s = w.method_8320(pos);
        return s.method_26215() || s.method_26220((class_1922) w, pos).method_1110();
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
