package dev.rex.farmbuilder.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Flight route tests on a fake world: arrival, never entering a block, step size. */
public class FlyTest {
    static Set<String> solid = new HashSet<>();
    static int failures;

    static void wall(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) solid.add(x + "," + y + "," + z);
    }

    static void ground() {
        solid.clear();
        wall(-50, 50, -1, -1, -50, 50);
    }

    static boolean cellFree(int x, int y, int z) {
        return !solid.contains(x + "," + y + "," + z) && !solid.contains(x + "," + (y + 1) + "," + z);
    }

    static boolean boxFree(double x, double y, double z) {
        for (int i = (int) Math.floor(x - 0.299); i <= (int) Math.floor(x + 0.299); i++)
            for (int j = (int) Math.floor(y + 0.001); j <= (int) Math.floor(y + 1.799); j++)
                for (int k = (int) Math.floor(z - 0.299); k <= (int) Math.floor(z + 0.299); k++)
                    if (solid.contains(i + "," + j + "," + k)) return false;
        return true;
    }

    static void run(String name, boolean expectRoute, double px, double py, double pz, int gx, int gy, int gz) {
        int sx = (int) Math.floor(px), sy = (int) Math.floor(py), sz = (int) Math.floor(pz);
        List<int[]> r = FlyMath.route(sx, sy, sz, gx, gy, gz, 8, 30000, FlyTest::cellFree);
        if (r == null) {
            System.out.println((expectRoute ? "FAIL " : "ok   ") + name + ": no route");
            if (expectRoute) failures++;
            return;
        }
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{sx + 0.5, sy, sz + 0.5});
        for (int[] c : FlyMath.corners(sx, sy, sz, r)) pts.add(new double[]{c[0] + 0.5, c[1], c[2] + 0.5});
        double[] p = {px, py, pz};
        int t = 0;
        for (double[] w : pts) {
            while (p[0] != w[0] || p[1] != w[1] || p[2] != w[2]) {
                double[] n = FlyMath.toward(p[0], p[1], p[2], w[0], w[1], w[2], 0.6);
                double step = Math.sqrt(Math.pow(n[0] - p[0], 2) + Math.pow(n[1] - p[1], 2) + Math.pow(n[2] - p[2], 2));
                if (!boxFree(n[0], n[1], n[2]) || step > 0.6001 || ++t > 2000) {
                    System.out.println("FAIL " + name + ": bad step to " + Arrays.toString(n));
                    failures++;
                    return;
                }
                p = n;
            }
        }
        boolean ok = expectRoute && p[0] == gx + 0.5 && p[1] == gy && p[2] == gz + 0.5;
        if (!ok) failures++;
        System.out.println((ok ? "ok   " : "FAIL ") + name + ": " + t + " ticks");
    }

    public static void main(String[] a) {
        ground();
        run("open field", true, 0.5, 0, 0.5, 10, 0, 0);
        run("up to height", true, 0.5, 0, 0.5, 3, 12, 7);
        run("down from height", true, 3.5, 12, 7.5, 0, 0, 0);
        run("off-center start", true, 0.81, 0, 0.12, 4, 3, 0);
        wall(5, 5, 0, 6, -10, 10);
        run("over a wall", true, 0.5, 0, 0.5, 9, 0, 0);
        ground();
        wall(-3, 8, 4, 4, -3, 3);
        wall(5, 5, 0, 3, -10, 10);
        run("wall under a roof", true, 0.5, 0, 0.5, 9, 0, 0);
        ground();
        wall(-1, 1, 0, 8, 2, 2);
        run("over a tower", true, 0.5, 0, 0.5, 0, 0, 5);
        ground();
        wall(-4, 4, 0, 5, -4, 4);
        for (int x = -3; x <= 3; x++) for (int y = 0; y <= 4; y++) for (int z = -3; z <= 3; z++) solid.remove(x + "," + y + "," + z);
        run("sealed room", false, 0.5, 0, 0.5, 8, 0, 0);
        for (int[] c : new int[][]{{-5, -60, 7}, {30000000, 319, -30000000}, {-1, -1, -1}}) {
            if (!Arrays.equals(c, FlyMath.unkey(FlyMath.key(c[0], c[1], c[2])))) {
                System.out.println("FAIL key " + Arrays.toString(c));
                failures++;
            }
        }
        System.out.println(failures == 0 ? "ALL PASS" : failures + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }
}
