package net.minecraft;
import java.util.*;
import meteordevelopment.meteorclient.MeteorClient;
public class Sim {
    public static final double W = 0.3, H = 1.8, STEP_UP = 0.6, REACH = 4.5;
    public final class_638 world = new class_638();
    public final class_746 player = new class_746();
    public final class_310 mc = new class_310();
    public int placed, broken, rejected;

    public Sim() {
        MeteorClient.mc = mc;
        mc.field_1687 = world;
        mc.field_1724 = player;
        mc.field_1761 = new class_636(this);
        player.inventory.selected = 0;
    }

    // ---------------------------------------------------------------- world helpers
    public static boolean solid(class_2680 s) { return !s.method_26215() && !s.method_26204().isFluid(); }

    public static class_3965 raycast(class_638 world, class_243 a, class_243 b) {
        double dx = b.field_1352 - a.field_1352, dy = b.field_1351 - a.field_1351, dz = b.field_1350 - a.field_1350;
        int x = (int) Math.floor(a.field_1352), y = (int) Math.floor(a.field_1351), z = (int) Math.floor(a.field_1350);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        double tMaxX = dx == 0 ? Double.MAX_VALUE : ((sx > 0 ? x + 1 : x) - a.field_1352) / dx;
        double tMaxY = dy == 0 ? Double.MAX_VALUE : ((sy > 0 ? y + 1 : y) - a.field_1351) / dy;
        double tMaxZ = dz == 0 ? Double.MAX_VALUE : ((sz > 0 ? z + 1 : z) - a.field_1350) / dz;
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        class_2350 face = null;
        double t = 0;
        for (int guard = 0; guard < 64; guard++) {
            class_2338 p = new class_2338(x, y, z);
            if (guard > 0 && solid(world.method_8320(p))) {
                class_243 hit = new class_243(a.field_1352 + dx * t, a.field_1351 + dy * t, a.field_1350 + dz * t);
                return new class_3965(hit, face, p, false);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > 1.0) break;
                t = tMaxX; x += sx; tMaxX += tDeltaX; face = sx > 0 ? class_2350.WEST : class_2350.EAST;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > 1.0) break;
                t = tMaxY; y += sy; tMaxY += tDeltaY; face = sy > 0 ? class_2350.DOWN : class_2350.UP;
            } else {
                if (tMaxZ > 1.0) break;
                t = tMaxZ; z += sz; tMaxZ += tDeltaZ; face = sz > 0 ? class_2350.NORTH : class_2350.SOUTH;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- placement / breaking
    public static Map<class_2338, class_2680> WANTED = new HashMap<>();
    public static int DEBUG_BAD = 0;

    public class_1269 place(class_746 p, class_3965 hit) {
        class_2338 target = hit.method_17777().method_10093(hit.method_17780());
        if (solid(world.method_8320(target))) { rejected++; return new SimResult(false); }
        if (overlaps(p, target)) { rejected++; return new SimResult(false); }
        class_1799 stack = p.inventory.method_5438(p.inventory.method_67532());
        if (stack.method_7960() || !(stack.method_7909() instanceof class_1747 item)) { rejected++; return new SimResult(false); }
        class_2248 block = item.method_7711();
        class_2680 state = block.method_9605(new class_1750(new class_1838(p, class_1268.field_5808, hit)));
        class_2680 want = WANTED.get(target);
        if (want != null && !want.equals(state) && DEBUG_BAD++ < 6) {
            float gy = -999, gp = -999; int tick = -1;
            try { java.lang.reflect.Field f1 = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredField("sentYaw"); f1.setAccessible(true); gy = f1.getFloat(null); } catch (Exception e) { }
            System.out.println("  guardYaw=" + gy + " ");
            if (DEBUG_BAD == 2) new Throwable("BAD stack").printStackTrace(System.out);
            System.out.println("  BAD place at " + target + " state " + state + " want " + want + " yaw=" + p.yaw + " pitch=" + p.pitch
                + " hit=(" + hit.method_17784().field_1352 + "," + hit.method_17784().field_1351 + "," + hit.method_17784().field_1350 + ") side=" + hit.method_17780() + " sup=" + hit.method_17777());
        }
        world.method_8501(target, state);
        stack.shrink();
        placed++;
        return new SimResult(true);
    }

    public void breakBlock(class_2338 pos) {
        if (!world.method_8320(pos).method_26215()) { world.method_8501(pos, class_638.AIR()); broken++; }
    }

    /** Player box (0.6 x 1.8) overlapping the block cell. */
    public static boolean overlaps(class_746 p, class_2338 c) {
        return c.method_10263() + 1 > p.x - W && c.method_10263() < p.x + W
            && c.method_10260() + 1 > p.z - W && c.method_10260() < p.z + W
            && c.method_10264() + 1 > p.y && c.method_10264() < p.y + H;
    }

    // ---------------------------------------------------------------- player physics
    /**
     * Minecraft-like tick: input -> velocity, move (Y first, then X and Z, sub-stepped so contacts
     * land exactly on block faces, with a 0.6 step-up while walking), then gravity. Jump apex ~1.25.
     */
    public void tickPlayer(boolean fwd, boolean back, boolean left, boolean right, boolean jump, double speed) {
        double rad = Math.toRadians(player.yaw);
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double mx = 0, mz = 0;
        if (fwd) { mx += fx; mz += fz; }
        if (back) { mx -= fx; mz -= fz; }
        if (left) { mx += fz; mz -= fx; }
        if (right) { mx -= fz; mz += fx; }
        double len = Math.hypot(mx, mz);
        if (len > 1e-6) { mx = mx / len * speed; mz = mz / len * speed; }
        if (player.onGround) {
            player.vx = mx; player.vz = mz;
            if (jump) { player.vy = 0.42; player.onGround = false; }
        } else {
            player.vx = mx; player.vz = mz;
        }
        // vertical
        boolean fellOn = false;
        double dy = player.vy;
        int n = Math.max(1, (int) Math.ceil(Math.abs(dy) / 0.02));
        double sy = dy / n;
        for (int i = 0; i < n; i++) {
            if (collides(player.x, player.y + sy, player.z)) { fellOn = dy < 0; player.vy = 0; break; }
            player.y += sy;
        }
        // horizontal, with a step-up while on the ground
        player.x = moveHorizontal(player.x, player.vx, true);
        player.z = moveHorizontal(player.z, player.vz, false);
        player.onGround = fellOn || (player.vy <= 0 && collides(player.x, player.y - 0.01, player.z));
        if (player.onGround) { player.vy = 0; }
        else { player.vy = (player.vy - 0.08) * 0.98; }
    }

    private double moveHorizontal(double pos, double d, boolean isX) {
        if (Math.abs(d) < 1e-9) return pos;
        int n = Math.max(1, (int) Math.ceil(Math.abs(d) / 0.02));
        double st = d / n;
        double p = pos;
        for (int i = 0; i < n; i++) {
            double np = p + st;
            boolean hit = isX ? collides(np, player.y, player.z) : collides(player.x, player.y, np);
            if (hit) {
                // step up a ledge of at most 0.6 when walking on the ground
                if (player.onGround && !(isX ? collides(np, player.y + STEP_UP, player.z) : collides(player.x, player.y + STEP_UP, np))) {
                    player.y += STEP_UP;
                    p = np;
                    continue;
                }
                return p;
            }
            p = np;
        }
        return p;
    }

    public boolean collides(double x, double y, double z) {
        int x0 = (int) Math.floor(x - W + 1e-6), x1 = (int) Math.floor(x + W - 1e-6);
        int z0 = (int) Math.floor(z - W + 1e-6), z1 = (int) Math.floor(z + W - 1e-6);
        int y0 = (int) Math.floor(y + 1e-6), y1 = (int) Math.floor(y + H - 1e-6);
        for (int i = x0; i <= x1; i++) for (int j = y0; j <= y1; j++) for (int k = z0; k <= z1; k++) {
            if (solid(world.method_8320(new class_2338(i, j, k)))) return true;
        }
        return false;
    }

    public void lookCrosshair() {
        class_243 eye = player.method_33571();
        double pr = Math.toRadians(player.pitch), yr = Math.toRadians(player.yaw);
        double dx = -Math.sin(yr) * Math.cos(pr), dy = -Math.sin(pr), dz = Math.cos(yr) * Math.cos(pr);
        class_243 end = new class_243(eye.field_1352 + dx * REACH, eye.field_1351 + dy * REACH, eye.field_1350 + dz * REACH);
        mc.field_1765 = raycast(world, eye, end);
    }
}
