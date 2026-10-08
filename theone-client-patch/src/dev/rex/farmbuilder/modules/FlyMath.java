package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure flight planning for creative (no Minecraft types, unit-testable). */
final class FlyMath {
    /** True when the player's body fits with its feet in this cell (the cell and the one above are free). */
    interface Cell {
        boolean free(int x, int y, int z);
    }

    private static final int[][] DIRS = {{0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};

    private FlyMath() {
    }

    /**
     * Shortest route of feet cells from start to goal through free cells (breadth first, 6
     * directions), searched inside the box around both points grown by `margin`. Returns the cells
     * after the start, ending with the goal, or null when there is no route within maxNodes.
     */
    static List<int[]> route(int sx, int sy, int sz, int gx, int gy, int gz, int margin, int maxNodes, Cell cell) {
        if (!cell.free(gx, gy, gz)) {
            return null;
        }
        int x0 = Math.min(sx, gx) - margin;
        int x1 = Math.max(sx, gx) + margin;
        int y0 = Math.min(sy, gy) - margin;
        int y1 = Math.max(sy, gy) + margin;
        int z0 = Math.min(sz, gz) - margin;
        int z1 = Math.max(sz, gz) + margin;
        Map<Long, Long> prev = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        long start = key(sx, sy, sz);
        long goal = key(gx, gy, gz);
        prev.put(start, start);
        queue.add(new int[]{sx, sy, sz});
        while (!queue.isEmpty() && prev.size() < maxNodes) {
            int[] c = queue.poll();
            long ck = key(c[0], c[1], c[2]);
            if (ck == goal) {
                List<int[]> out = new ArrayList<>();
                long k = ck;
                while (k != start) {
                    out.add(0, unkey(k));
                    k = prev.get(k);
                }
                return out;
            }
            for (int[] d : DIRS) {
                int nx = c[0] + d[0];
                int ny = c[1] + d[1];
                int nz = c[2] + d[2];
                if (nx < x0 || nx > x1 || ny < y0 || ny > y1 || nz < z0 || nz > z1) {
                    continue;
                }
                long nk = key(nx, ny, nz);
                if (prev.containsKey(nk) || !cell.free(nx, ny, nz)) {
                    continue;
                }
                prev.put(nk, ck);
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return null;
    }

    /** Drops the cells in the middle of straight runs, keeping the corners and the end. */
    static List<int[]> corners(int sx, int sy, int sz, List<int[]> cells) {
        List<int[]> out = new ArrayList<>();
        int[] prev = {sx, sy, sz};
        for (int i = 0; i < cells.size(); ++i) {
            int[] c = cells.get(i);
            if (i + 1 < cells.size()) {
                int[] n = cells.get(i + 1);
                boolean straight = n[0] - c[0] == c[0] - prev[0] && n[1] - c[1] == c[1] - prev[1] && n[2] - c[2] == c[2] - prev[2];
                if (straight) {
                    prev = c;
                    continue;
                }
            }
            out.add(c);
            prev = c;
        }
        return out;
    }

    /** Point at most `step` from p toward w. */
    static double[] toward(double px, double py, double pz, double wx, double wy, double wz, double step) {
        double dx = wx - px;
        double dy = wy - py;
        double dz = wz - pz;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d <= step) {
            return new double[]{wx, wy, wz};
        }
        return new double[]{px + dx / d * step, py + dy / d * step, pz + dz / d * step};
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (long) (y & 0xFFF);
    }

    static int[] unkey(long k) {
        int x = (int) (k >> 38);
        int z = (int) (k << 26 >> 38);
        int y = (int) (k << 52 >> 52);
        return new int[]{x, y, z};
    }
}
