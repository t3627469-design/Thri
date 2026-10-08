import java.lang.reflect.*;
import java.util.*;
import net.minecraft.*;
import meteordevelopment.meteorclient.MeteorClient;
import dev.rex.farmbuilder.modules.OnlyBuild;
import meteordevelopment.meteorclient.systems.modules.Module;

/** Runs the real patched only-build against a simulated 7x7 diamond tower. */
public class Driver {
    static class_2248 block(String id, class_2769... props) {
        return new class_2248(id, false, false, props);
    }

    public static void main(String[] args) throws Exception {
        int maxTicks = args.length > 0 ? Integer.parseInt(args[0]) : 6000;
        Sim sim = new Sim();

        class_2769 facing = new class_2769("facing", List.of(class_2350.NORTH, class_2350.SOUTH, class_2350.EAST, class_2350.WEST));
        class_2769 half = new class_2769("half", List.of("bottom", "top"));
        class_2248 air = class_2246.AIR;
        class_2248 stone = block("minecraft:stone");
        class_2248 stairs = block("minecraft:oak_stairs", facing, half);
        for (String n : new String[]{"cobblestone", "dirt", "netherrack", "cobbled_deepslate", "andesite", "diorite", "granite", "tuff", "deepslate", "sandstone", "oak_planks", "spruce_planks"}) {
            block("minecraft:" + n).method_8389();
        }
        stone.method_8389();
        stairs.method_8389();

        // ground at y=-1 (not part of the schematic)
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) sim.world.method_8501(new class_2338(x, -1, z), new class_2680(stone, stone.defaults));

        // the schematic: 7x7 base, diamond layers above
        Map<class_2338, class_2680> target = new LinkedHashMap<>();
        for (int y = 0; y <= 4; y++) {
            int r = y == 0 ? 99 : (y == 1 ? 3 : (y == 2 ? 2 : (y == 3 ? 1 : 0)));
            for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
                int d = Math.abs(x - 3) + Math.abs(z - 3);
                if (y == 0) { if (x > 6 || z > 6) continue; }
                else if (d > r) continue;
                class_2680 st = (y == 2)
                    ? new class_2680(stairs, Map.of(facing, class_2350.NORTH, half, "bottom"))
                    : new class_2680(stone, stone.defaults);
                target.put(new class_2338(x, y, z), st);
            }
        }
        System.out.println("schematic blocks: " + target.size());

        // inventory: hotbar 0 stone, 1 stairs, 2 cobblestone (pillar spare)
        class_1661 inv = sim.player.inventory;
        inv.method_5447(0, new class_1799(class_1792.BY_NAME.get("minecraft:stone")));
        inv.method_5438(0).method_7939(200);
        inv.method_5447(1, new class_1799(class_1792.BY_NAME.get("minecraft:oak_stairs")));
        inv.method_5438(1).method_7939(40);
        inv.method_5447(2, new class_1799(class_1792.BY_NAME.get("minecraft:cobblestone")));
        inv.method_5438(2).method_7939(64);

        sim.player.x = -2.5; sim.player.y = 0; sim.player.z = 3.5; sim.player.yaw = 90; sim.player.pitch = 0;

        long seed = Long.parseLong(System.getenv().getOrDefault("SEED", "1"));
        OnlyBuild ob = new OnlyBuild();
        {
            Field rf = OnlyBuild.class.getDeclaredField("random"); rf.setAccessible(true);
            rf.set(ob, new Random(seed));
            Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
            sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
            Class<?> look = Class.forName("dev.rex.farmbuilder.modules.Look");
            Field nf = look.getDeclaredField("NOISE"); 
            u.putObject(u.staticFieldBase(nf), u.staticFieldOffset(nf), new Random(seed));
        }
        Field wf = OnlyBuild.class.getDeclaredField("wanted"); wf.setAccessible(true);
        @SuppressWarnings("unchecked") Map<class_2338, class_2680> wanted = (Map<class_2338, class_2680>) wf.get(ob);
        Field pf = OnlyBuild.class.getDeclaredField("planner"); pf.setAccessible(true);
        Object planner = pf.get(ob);
        Method add = planner.getClass().getDeclaredMethod("add", class_2338.class); add.setAccessible(true);
        wanted.clear();
        Sim.WANTED = target;
        for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
            wanted.put(e.getKey(), e.getValue());
            add.invoke(planner, e.getKey());
        }
        // what onActivate() does after load(): the Baritone schematic read is replaced by the map above
        set(ob, "started", true);
        set(ob, "phase", Enum.valueOf((Class) Class.forName("dev.rex.farmbuilder.modules.OnlyBuild$Phase"), "BUILD"));
        set(ob, "total", target.size());
        set(ob, "origin", new class_2338(0, 0, 0));
        set(ob, "startedAt", System.currentTimeMillis());
        Method mpl = planner.getClass().getDeclaredMethod("size"); mpl.setAccessible(true);
        System.out.println("planner size " + mpl.invoke(planner));

        Method onTick = OnlyBuild.class.getDeclaredMethod("onTick", meteordevelopment.meteorclient.events.world.TickEvent$Post.class);
        onTick.setAccessible(true);
        Object tickEvent = meteordevelopment.meteorclient.events.world.TickEvent$Post.class.getDeclaredConstructor().newInstance();

        boolean printer = System.getenv("PRINTER") != null;
        if (printer) {
            java.lang.reflect.Method sp = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("setPrinting", boolean.class);
            sp.setAccessible(true);
            sp.invoke(null, true);
        }
        java.lang.reflect.Method printerTick = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("printerTick", Map.class, int.class, double.class, Module.class);
        printerTick.setAccessible(true);
        double[][] spots = {{-2.5, 0, 3.5}, {8.5, 0, 3.5}, {3.5, 0, -2.5}, {3.5, 0, 8.5}, {1.5, 1, 1.5}, {5.5, 1, 5.5}, {1.5, 1, 5.5}, {5.5, 1, 1.5}};
        int phase = 0;
        int phaseStart = 0, placedAtPhase = 0;
        double[] phaseFrom = null;
        int lastPlaced = -1;
        String lastMode = "OFF";
        if (printer) { sim.player.x = spots[0][0]; sim.player.y = spots[0][1]; sim.player.z = spots[0][2]; phaseFrom = spots[0]; }
        // a printing player walks to the standable cell that can place the most remaining blocks, stands there
        // for ROUND ticks, and repeats; the bot itself never moves.
        final int ROUND = 1500;
        int rounds = 0;
        java.lang.reflect.Method bestM = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("best", class_2338.class, class_2680.class, class_243.class, boolean.class);
        bestM.setAccessible(true);
        if (printer) { int[] pick = pickSpot(sim, target, bestM); sim.player.x = pick[0] + 0.5; sim.player.y = pick[1]; sim.player.z = pick[2] + 0.5; sim.player.vx = sim.player.vy = sim.player.vz = 0; System.out.println("ROUND 0 stand " + java.util.Arrays.toString(pick)); }
        for (int t = 1; t <= maxTicks; t++) {
            if (System.getenv("PSTATE") != null && printer && t % 500 == 0 && t > 9000 && t < 10100) {
                StringBuilder ps = new StringBuilder("  PST t" + t + " player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") yaw=" + round(sim.player.yaw) + " pitch=" + round(sim.player.pitch));
                for (String fn : new String[]{"cur", "walkTo", "lastWhy", "activeY"}) {
                    Field pf3 = planner.getClass().getDeclaredField(fn); pf3.setAccessible(true);
                    ps.append(" ").append(fn).append("=").append(pf3.get(planner));
                }
                Field dl = OnlyBuild.class.getDeclaredField("doing"); dl.setAccessible(true);
                ps.append(" doing='").append(dl.get(ob)).append("' size=").append(planner.getClass().getDeclaredMethod("size").invoke(planner));
                System.out.println(ps);
            }
            if (System.getenv("PDBG") != null && printer && t == 9100) {
                class_243 eye = sim.player.method_33571();
                java.lang.reflect.Method fp = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("findPlacement", class_2338.class);
                fp.setAccessible(true);
                System.out.println("  PDBG player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") yaw=" + round(sim.player.yaw));
                for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
                    class_2680 have = sim.world.method_8320(e.getKey());
                    if (have.equals(e.getValue())) continue;
                    double d = eye.method_1022(class_243.method_24953(e.getKey()));
                    if (d > 5.0) continue;
                    Object pl = fp.invoke(null, e.getKey());
                    System.out.println("  miss " + e.getKey() + " d=" + round(d) + " have=" + have + " findPlacement=" + (pl == null ? "null" : "yes"));
                    if (e.getKey().equals(new class_2338(4, 0, 5))) {
                        java.lang.reflect.Method nf = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("nearestFace", class_2338.class, class_243.class);
                        nf.setAccessible(true);
                        System.out.println("  nearestFace(4,0,5)=" + nf.invoke(null, e.getKey(), eye));
                        java.lang.reflect.Method vis = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("visible", class_243.class, class_243.class, class_2338.class, class_2350.class);
                        vis.setAccessible(true);
                        System.out.println("  visible to ground top = " + vis.invoke(null, eye, new class_243(4.5, -0.05, 5.5), new class_2338(4, -1, 5), class_2350.UP));
                        class_3965 rr = Sim.raycast(sim.world, eye, new class_243(4.5, -0.05, 5.5));
                        System.out.println("  raw ray hit: " + (rr == null ? "null" : rr.method_17777() + " side " + rr.method_17780() + " at " + rr.method_17784().field_1351));
                    }
                }
            }
            if (printer && t > 1 && (t - 1) % ROUND == 0 && rounds < 14) {
                rounds++;
                int[] pick = pickSpot(sim, target, bestM);
                System.out.println("ROUND " + rounds + ": placed " + (sim.placed - placedAtPhase) + " last round; stand " + (pick == null ? "none" : java.util.Arrays.toString(pick)));
                placedAtPhase = sim.placed;
                if (pick == null) break;
                sim.player.x = pick[0] + 0.5; sim.player.y = pick[1]; sim.player.z = pick[2] + 0.5;
                sim.player.vx = 0; sim.player.vy = 0; sim.player.vz = 0;
            }
            sim.lookCrosshair();
            if (System.getenv("PSTATE") != null && t >= 2130 && t <= 2150) {
                StringBuilder ps = new StringBuilder("  PSTATE t" + t);
                for (String fn : new String[]{"cur", "walkTo", "lastWhy", "activeY", "curTicks", "unloadedWalk"}) {
                    Field pf3 = planner.getClass().getDeclaredField(fn); pf3.setAccessible(true);
                    ps.append(" ").append(fn).append("=").append(pf3.get(planner));
                }
                Field nt = OnlyBuild.class.getDeclaredField("noneTicks"); nt.setAccessible(true);
                Field dl = OnlyBuild.class.getDeclaredField("delay"); dl.setAccessible(true);
                Field ak = OnlyBuild.class.getDeclaredField("aimTarget"); ak.setAccessible(true);
                ps.append(" noneTicks=").append(nt.get(ob)).append(" delay=").append(dl.get(ob)).append(" aimTarget=").append(ak.get(ob));
                ps.append(" yaw=").append(round(sim.player.yaw)).append(" pitch=").append(round(sim.player.pitch)).append(" crosshair=").append(sim.mc.field_1765 == null ? "null" : ((class_3965) sim.mc.field_1765).method_17777() + "/" + ((class_3965) sim.mc.field_1765).method_17780());
                System.out.println(ps);
                if (t == 2136) {
                    class_2338 tp = new class_2338(2, 2, 3);
                    java.lang.reflect.Method fpm = Class.forName("dev.rex.farmbuilder.modules.Look").getDeclaredMethod("findPlacement", class_2338.class); fpm.setAccessible(true); Object pl = fpm.invoke(null, tp);
                    System.out.println("  PL t2136 placement=" + pl);
                    if (pl != null) {
                        Method hitM = pl.getClass().getDeclaredMethod("hit"); hitM.setAccessible(true);
                        Method agM = pl.getClass().getDeclaredMethod("against"); agM.setAccessible(true);
                        Method sdM = pl.getClass().getDeclaredMethod("side"); sdM.setAccessible(true);
                        class_243 hp = (class_243) hitM.invoke(pl);
                        class_243 eye = sim.player.method_33571();
                        System.out.println("  eye=(" + eye.field_1352 + "," + eye.field_1351 + "," + eye.field_1350 + ") hit=(" + hp.field_1352 + "," + hp.field_1351 + "," + hp.field_1350 + ") against=" + agM.invoke(pl) + " side=" + sdM.invoke(pl));
                        class_3965 ray = Sim.raycast(sim.world, eye, hp);
                        System.out.println("  ray to hit hits: " + (ray == null ? "nothing" : ray.method_17777() + "/" + ray.method_17780()));
                        double ya[] = {0};
                        java.lang.reflect.Method atm = Class.forName("dev.rex.farmbuilder.modules.Look").getDeclaredMethod("anglesTo", class_243.class); atm.setAccessible(true); float[] ang = (float[]) atm.invoke(null, hp);
                        System.out.println("  anglesTo hit yaw=" + ang[0] + " pitch=" + ang[1] + " ; player yaw=" + sim.player.yaw + " pitch=" + sim.player.pitch);
                    }
                }
            }
            if (System.getenv("PILLAR") != null) {
                Field pf2 = Class.forName("dev.rex.farmbuilder.modules.Climb").getDeclaredField("pillar"); pf2.setAccessible(true);
                Object pill = pf2.get(null);
                Method pm = pill.getClass().getDeclaredMethod("mode"); pm.setAccessible(true);
                String mode = String.valueOf(pm.invoke(pill));
                if (!mode.equals(lastMode)) {
                    System.out.println("  t" + t + " pillar " + lastMode + " -> " + mode + " at (" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") " + status(ob, planner));
                    lastMode = mode;
                }
            }
            baritone.api.SimBaritone.PROVIDER.tick(sim);
            if (printer) printerTick.invoke(null, wanted, 4, 4.5, ob); else onTick.invoke(ob, tickEvent);
            if (printer && System.getenv("PTRACE2") != null && t >= 1210 && t <= 1214) {
                java.lang.reflect.Method fp3 = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("findPlacement", class_2338.class);
                fp3.setAccessible(true);
                class_243 e3 = sim.player.method_33571();
                class_2338 bp = null; Object bpl = null; double bd = 1e9;
                for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
                    if (sim.world.method_8320(e.getKey()).equals(e.getValue())) continue;
                    double dd = e3.method_1022(class_243.method_24953(e.getKey()));
                    if (dd > 5.0 || dd >= bd) continue;
                    Object pl = fp3.invoke(null, e.getKey());
                    if (pl != null) { bp = e.getKey(); bpl = pl; bd = dd; }
                }
                if (bpl != null) {
                    java.lang.reflect.Method hitM = bpl.getClass().getDeclaredMethod("hit"); hitM.setAccessible(true);
                    java.lang.reflect.Method agM = bpl.getClass().getDeclaredMethod("against"); agM.setAccessible(true);
                    java.lang.reflect.Method sdM = bpl.getClass().getDeclaredMethod("side"); sdM.setAccessible(true);
                    class_243 hp = (class_243) hitM.invoke(bpl);
                    class_3965 ch = (class_3965) sim.mc.field_1765;
                    java.lang.reflect.Method atm = Class.forName("dev.rex.farmbuilder.modules.Look").getDeclaredMethod("anglesTo", class_243.class); atm.setAccessible(true);
                    float[] ang = (float[]) atm.invoke(null, hp);
                    System.out.println("  PT2 t" + t + " pick=" + bp + " against=" + agM.invoke(bpl) + " side=" + sdM.invoke(bpl) + " hit=(" + round(hp.field_1352) + "," + round(hp.field_1351) + "," + round(hp.field_1350) + ")"
                        + " wantYaw=" + round(ang[0]) + " wantPitch=" + round(ang[1]) + " yaw=" + round(sim.player.yaw) + " pitch=" + round(sim.player.pitch)
                        + " crosshair=" + (ch == null ? "null" : ch.method_17777() + "/" + ch.method_17780()) + " eye=(" + round(e3.field_1352) + "," + round(e3.field_1351) + "," + round(e3.field_1350) + ")");
                }
            }
            if (printer && System.getenv("PTRACE") != null && t % 300 == 0 && t >= 1200 && t <= 2400) {
                java.lang.reflect.Method fp2 = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("findPlacement", class_2338.class);
                fp2.setAccessible(true);
                class_243 e2 = sim.player.method_33571();
                int cnt = 0; StringBuilder sb = new StringBuilder();
                for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
                    if (sim.world.method_8320(e.getKey()).equals(e.getValue())) continue;
                    if (e2.method_1022(class_243.method_24953(e.getKey())) > 5.0) continue;
                    Object pl = fp2.invoke(null, e.getKey());
                    if (pl != null) { cnt++; if (sb.length() < 200) sb.append(e.getKey()).append(' '); }
                }
                System.out.println("  PTRACE t" + t + " player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") placeable=" + cnt + " " + sb + " yaw=" + round(sim.player.yaw) + " pitch=" + round(sim.player.pitch) + " placed=" + sim.placed);
            }
            boolean fwd = sim.mc.field_1690.field_1894.pressed, back = sim.mc.field_1690.field_1881.pressed;
            boolean left = sim.mc.field_1690.field_1913.pressed, right = sim.mc.field_1690.field_1849.pressed;
            boolean jump = sim.mc.field_1690.field_1903.pressed;
            if (System.getenv("DBG") != null && t <= 40) System.out.println("  t" + t + " keys f" + fwd + " b" + back + " l" + left + " r" + right + " j" + jump + " pos(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") yaw " + round(sim.player.yaw) + " on " + sim.player.onGround + " vy " + round(sim.player.vy));
            sim.tickPlayer(fwd, back, left, right, jump, 0.2);
            if (t % 500 == 0 || (System.getenv("DBG2") != null && t % 100 == 0 && t > 1500 && t < 2100)) {
                System.out.println("tick " + t + " placed=" + sim.placed + " broken=" + sim.broken + " rejected=" + sim.rejected
                    + " " + status(ob, planner) + " player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ")");
            }
            if (!printer && t % 2000 == 0) {
                if (sim.placed == lastPlaced) { System.out.println("no placement in 2000 ticks at " + t + "; status: " + status(ob, planner)); break; }
                lastPlaced = sim.placed;
            }
            if (!printer && ob.isActive() == false) { System.out.println("module off at " + t); break; }
        }
        System.out.println("END player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") yaw=" + round(sim.player.yaw) + " pitch=" + round(sim.player.pitch) + " on=" + sim.player.onGround + " keys f=" + sim.mc.field_1690.field_1894.pressed + " j=" + sim.mc.field_1690.field_1903.pressed);
        int ok = 0, wrong = 0, missing = 0;
        List<String> bad = new ArrayList<>();
        for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
            class_2680 have = sim.world.method_8320(e.getKey());
            if (have.equals(e.getValue())) ok++;
            else if (have.method_26215()) { missing++; if (bad.size() < 12) bad.add("missing " + e.getKey()); }
            else { wrong++; if (bad.size() < 12) bad.add("wrong " + e.getKey() + " have " + have + " want " + e.getValue()); }
        }
        int extra = 0;
        for (class_2338 p : sim.world.blocks.keySet()) {
            if (!target.containsKey(p) && p.method_10264() >= 0 && !(p.method_10264() == -1)) extra++;
        }
        java.lang.reflect.Method skm = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("skipped");
        skm.setAccessible(true);
        System.out.println("Guard.skipped = " + skm.invoke(null));
        System.out.println("baritone paths found=" + baritone.api.SimBaritone.pathsFound + " failed=" + baritone.api.SimBaritone.pathsFailed);
        System.out.println("RESULT ok=" + ok + " wrong=" + wrong + " missing=" + missing + " extraBlocks=" + extra
            + " placed=" + sim.placed + " broken=" + sim.broken + " rejected=" + sim.rejected);
        for (String b : bad) System.out.println("  " + b);
        System.exit(0);
    }

    static void set(Object o, String name, Object v) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true);
        if (f.getType() == boolean.class) f.setBoolean(o, (Boolean) v);
        else if (f.getType() == int.class) f.setInt(o, (Integer) v);
        else if (f.getType() == long.class) f.setLong(o, (Long) v);
        else f.set(o, v);
    }

    static String status(OnlyBuild ob, Object planner) {
        try {
            Field d = OnlyBuild.class.getDeclaredField("doing"); d.setAccessible(true);
            Field h = OnlyBuild.class.getDeclaredField("halted"); h.setAccessible(true);
            Field m = OnlyBuild.class.getDeclaredField("haltMessage"); m.setAccessible(true);
            Method sz = planner.getClass().getDeclaredMethod("size"); sz.setAccessible(true);
            Method ly = planner.getClass().getDeclaredMethod("activeLayerY"); ly.setAccessible(true);
            StringBuilder rem = new StringBuilder();
            Field wgf = OnlyBuild.class.getDeclaredField("walkGoal"); wgf.setAccessible(true);
            Field ssf = OnlyBuild.class.getDeclaredField("standSpot"); ssf.setAccessible(true);
            Field stf = OnlyBuild.class.getDeclaredField("standFor"); stf.setAccessible(true);
            Method remM = planner.getClass().getDeclaredMethod("remainingItems"); remM.setAccessible(true);
            Method ll = planner.getClass().getDeclaredMethod("view"); ll.setAccessible(true);
            Method lo = planner.getClass().getDeclaredMethod("lowestLayerY"); lo.setAccessible(true);
            Method hi = planner.getClass().getDeclaredMethod("nextLayerY"); hi.setAccessible(true);
            @SuppressWarnings("unchecked") List<class_2338> v = (List<class_2338>) ll.invoke(planner);
            rem.append(" view=").append(v.size()).append(v.size() > 0 ? v.subList(0, Math.min(6, v.size())) : "");
            rem.append(" walkGoal=").append(wgf.get(ob)).append(" standFor=").append(stf.get(ob)).append(" standSpot=").append(ssf.get(ob));
            return rem + " left=" + sz.invoke(planner) + " activeY=" + ly.invoke(planner) + " doing='" + d.get(ob) + "' halted=" + h.get(ob) + " " + m.get(ob);
        } catch (Exception e) { return e.toString(); }
    }

    /**
     * Standable cell (feet) from which the printer's own placement search finds the most remaining blocks
     * within reach, or null when no cell finds any. The player is moved to each candidate and put back.
     */
    static int[] pickSpot(Sim sim, Map<class_2338, class_2680> target, java.lang.reflect.Method unused) throws Exception {
        java.lang.reflect.Method fp = Class.forName("dev.rex.farmbuilder.modules.Guard").getDeclaredMethod("findPlacement", class_2338.class);
        fp.setAccessible(true);
        double ox = sim.player.x, oy = sim.player.y, oz = sim.player.z;
        int[] best = null;
        int bestCount = 0;
        for (int x = -2; x <= 9; x++) for (int y = 0; y <= 5; y++) for (int z = -2; z <= 9; z++) {
            if (sim.world.method_8320(new class_2338(x, y - 1, z)).method_26215()) continue;
            class_2338 feet = new class_2338(x, y, z), head = new class_2338(x, y + 1, z);
            sim.player.x = x + 0.5; sim.player.y = y; sim.player.z = z + 0.5;
            if (sim.collides(sim.player.x, sim.player.y, sim.player.z)) continue;
            class_243 eye = sim.player.method_33571();
            int count = 0;
            for (Map.Entry<class_2338, class_2680> e : target.entrySet()) {
                class_2680 have = sim.world.method_8320(e.getKey());
                if (have.equals(e.getValue())) continue;
                if (eye.method_1022(class_243.method_24953(e.getKey())) > 4.4) continue;
                if (fp.invoke(null, e.getKey()) != null && !overlap(e.getKey(), sim.player)) count++;
            }
            if (count > bestCount) { bestCount = count; best = new int[]{x, y, z}; }
        }
        sim.player.x = ox; sim.player.y = oy; sim.player.z = oz;
        return best;
    }

    static boolean overlap(class_2338 p, class_746 pl) {
        double x = pl.x, y = pl.y, z = pl.z;
        return p.method_10263() < x + 0.3 && p.method_10263() + 1 > x - 0.3
            && p.method_10260() < z + 0.3 && p.method_10260() + 1 > z - 0.3
            && p.method_10264() < y + 1.8 && p.method_10264() + 1 > y;
    }

    static double round(double d) { return Math.round(d * 10) / 10.0; }

    static Method findStart(Class<?> c) {
        for (Method m : c.getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && (m.getName().equals("start") || m.getName().equals("startBuild") || m.getName().equals("begin"))) return m;
        }
        return null;
    }
}
