import java.lang.reflect.*;
import java.util.*;
import net.minecraft.*;
import meteordevelopment.meteorclient.MeteorClient;
import dev.rex.farmbuilder.modules.OnlyBuild;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Runs the real patched only-build against a simulated 7x7 diamond tower.
 * PRINTER=1 activates the real Printer module first: the same only-build tick, but blocks in reach are placed
 * from where the player stands and the bot walks to the rest.
 */
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
        if (System.getenv("FARM") != null) {
            // the bundled farm instead of the tower: ground under its footprint, creative-style supply
            target = FarmLoad.load(System.getenv("FARM"));
            for (int x = -3; x <= 65; x++) for (int z = -3; z <= 57; z++) sim.world.method_8501(new class_2338(x, -1, z), new class_2680(stone, stone.defaults));
            sim.player.method_31549().field_7477 = true;
        }
        System.out.println("schematic blocks: " + target.size());

        // inventory: hotbar 0 stone, 1 stairs, 2 cobblestone (pillar spare)
        class_1661 inv = sim.player.inventory;
        inv.method_5447(0, new class_1799(class_1792.BY_NAME.get("minecraft:stone")));
        inv.method_5438(0).method_7939(200);
        if (System.getenv("NO_STAIRS") == null) {
            inv.method_5447(1, new class_1799(class_1792.BY_NAME.get("minecraft:oak_stairs")));
            inv.method_5438(1).method_7939(40);
        }
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
            Modules.get().add(ob);
            Field af = Module.class.getDeclaredField("active"); af.setAccessible(true); af.setBoolean(ob, true);
            Module printerModule = (Module) Class.forName("dev.rex.farmbuilder.modules.Printer").getDeclaredConstructor().newInstance();
            printerModule.toggle();
            System.out.println("printer active=" + printerModule.isActive());
        }
        sim.player.x = -2.5; sim.player.y = 0; sim.player.z = 3.5;
        int lastPlaced = -1;
        for (int t = 1; t <= maxTicks; t++) {
            sim.lookCrosshair();
            baritone.api.SimBaritone.PROVIDER.tick(sim);
            onTick.invoke(ob, tickEvent);
            boolean fwd = sim.mc.field_1690.field_1894.pressed, back = sim.mc.field_1690.field_1881.pressed;
            boolean left = sim.mc.field_1690.field_1913.pressed, right = sim.mc.field_1690.field_1849.pressed;
            boolean jump = sim.mc.field_1690.field_1903.pressed;
            sim.tickPlayer(fwd, back, left, right, jump, 0.2);
            if (t % 500 == 0) {
                System.out.println("tick " + t + " placed=" + sim.placed + " broken=" + sim.broken + " rejected=" + sim.rejected
                    + " " + status(ob, planner) + " player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ")");
            }
            if (t % 2000 == 0) {
                if (sim.placed == lastPlaced) { System.out.println("no placement in 2000 ticks at " + t + "; status: " + status(ob, planner)); break; }
                lastPlaced = sim.placed;
            }
        }
        System.out.println("END player=(" + round(sim.player.x) + "," + round(sim.player.y) + "," + round(sim.player.z) + ") yaw=" + round(sim.player.yaw) + " pitch=" + round(sim.player.pitch) + " on=" + sim.player.onGround);
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
            if (!target.containsKey(p) && p.method_10264() >= 0) extra++;
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
            return " left=" + sz.invoke(planner) + " activeY=" + ly.invoke(planner) + " doing='" + d.get(ob) + "' halted=" + h.get(ob) + " " + m.get(ob);
        } catch (Exception e) { return e.toString(); }
    }

    static double round(double d) { return Math.round(d * 10) / 10.0; }
}
