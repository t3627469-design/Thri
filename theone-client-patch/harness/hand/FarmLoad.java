import java.io.*;
import java.util.*;
import net.minecraft.*;

/**
 * The bundled farm (farms/sweet_berries.litematic), read from the palette and cells files that
 * harness/tools/export_farm.py writes. Blocks the planner never places (air, fluids, non-solid decorations)
 * are left out, so the target is the set only-build would load.
 */
public class FarmLoad {
    static final Set<String> SKIP = Set.of("minecraft:air", "minecraft:water", "minecraft:bubble_column",
        "minecraft:redstone_wire", "minecraft:glow_lichen", "minecraft:sweet_berry_bush", "minecraft:powered_rail",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:oak_wall_sign", "minecraft:oak_sign",
        "minecraft:ladder", "minecraft:lever", "minecraft:piston_head", "minecraft:lava_cauldron");

    static Comparable parse(String key, String v) {
        if (key.equals("facing")) return class_2350.valueOf(v.toUpperCase());
        if (v.equals("true") || v.equals("false")) return Boolean.valueOf(v);
        try { return Integer.valueOf(v); } catch (NumberFormatException e) { return v; }
    }

    public static Map<class_2338, class_2680> load(String dir) throws IOException {
        Map<Integer, String> names = new HashMap<>();
        Map<Integer, Map<String, String>> props = new HashMap<>();
        for (String line : java.nio.file.Files.readAllLines(new File(dir, "palette.tsv").toPath())) {
            String[] f = line.split("\t", -1);
            int i = Integer.parseInt(f[0]);
            names.put(i, f[1]);
            Map<String, String> m = new LinkedHashMap<>();
            if (f.length > 2 && !f[2].isEmpty()) for (String kv : f[2].split(";")) { String[] p = kv.split("=", 2); m.put(p[0], p[1]); }
            props.put(i, m);
        }
        // one block per name, with one property per key, values in the order they appear
        Map<String, class_2248> blocks = new HashMap<>();
        Map<String, Map<String, class_2769>> keys = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String n = names.get(i);
            if (SKIP.contains(n)) continue;
            Map<String, class_2769> k = keys.computeIfAbsent(n, x -> new LinkedHashMap<>());
            Map<String, List<Comparable>> vals = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : props.get(i).entrySet()) {
                vals.computeIfAbsent(e.getKey(), x -> new ArrayList<>());
            }
            for (Map.Entry<String, String> e : props.get(i).entrySet()) {
                Comparable c = parse(e.getKey(), e.getValue());
                if (!vals.get(e.getKey()).contains(c)) vals.get(e.getKey()).add(c);
            }
            for (String key : vals.keySet()) {
                if (!k.containsKey(key)) k.put(key, new class_2769(key, vals.get(key)));
            }
        }
        for (Map.Entry<String, Map<String, class_2769>> e : keys.entrySet()) {
            blocks.put(e.getKey(), new class_2248(e.getKey(), false, false, e.getValue().values().toArray(new class_2769[0])));
        }
        Map<class_2338, class_2680> target = new LinkedHashMap<>();
        int skipped = 0;
        for (String line : java.nio.file.Files.readAllLines(new File(dir, "cells.tsv").toPath())) {
            String[] f = line.split("\t");
            int x = Integer.parseInt(f[0]), y = Integer.parseInt(f[1]), z = Integer.parseInt(f[2]), idx = Integer.parseInt(f[3]);
            String n = names.get(idx);
            if (SKIP.contains(n)) { skipped++; continue; }
            class_2248 b = blocks.get(n);
            Map<class_2769, Comparable> state = new LinkedHashMap<>();
            for (Map.Entry<String, String> p : props.get(idx).entrySet()) {
                state.put(keys.get(n).get(p.getKey()), parse(p.getKey(), p.getValue()));
            }
            target.put(new class_2338(x, y, z), new class_2680(b, state));
        }
        System.out.println("farm: " + target.size() + " blocks to build, " + skipped + " left out (fluids/decorations)");
        return target;
    }
}
