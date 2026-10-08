package dev.theone.schematic.format;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Offline round-trip tests for the schematic readers. Each test writes a file in the real on-disk
 * format with an independent writer, reads it back through {@link Schematic#load} and compares
 * every block. Run with plain java (no test framework): exits non-zero on failure.
 */
public final class SchematicTest {
    private static int failures;

    public static void main(String[] args) throws IOException {
        Path dir = Files.createTempDirectory("schem-test");
        String[] palette = {"minecraft:air", "minecraft:stone", "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
            "minecraft:farmland[moisture=7]", "minecraft:wheat[age=0]", "minecraft:water[level=0]", "minecraft:oak_log[axis=x]"};

        // Odd sizes and a 7-entry palette (3 bits) make values straddle long boundaries.
        int sx = 7, sy = 5, sz = 9;
        int[][][] blocks = new int[sx][sy][sz];
        Random r = new Random(42);
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) blocks[x][y][z] = r.nextInt(palette.length);
        blocks[0][0][0] = 1; // keep the bounding box fixed
        blocks[sx - 1][sy - 1][sz - 1] = 1;

        // Big palette (5 bits) to exercise wider packing too.
        String[] bigPalette = new String[20];
        bigPalette[0] = "minecraft:air";
        for (int i = 1; i < 20; i++) bigPalette[i] = "minecraft:block_" + i;
        int[][][] bigBlocks = new int[sx][sy][sz];
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) bigBlocks[x][y][z] = r.nextInt(20);
        bigBlocks[0][0][0] = 3;
        bigBlocks[sx - 1][sy - 1][sz - 1] = 4;

        check("litematic 3-bit", load(dir, "a.litematic", litematic(palette, blocks, false)), palette, blocks);
        check("litematic 5-bit", load(dir, "b.litematic", litematic(bigPalette, bigBlocks, false)), bigPalette, bigBlocks);
        check("litematic negative size", load(dir, "c.litematic", litematic(palette, blocks, true)), palette, blocks);
        check("sponge v2", load(dir, "d.schem", sponge(palette, blocks, 2)), palette, blocks);
        check("sponge v3", load(dir, "e.schem", sponge(palette, blocks, 3)), palette, blocks);
        check("structure nbt", load(dir, "f.nbt", structure(palette, blocks)), palette, blocks);

        Schematic.StateSpec s = Schematic.StateSpec.parse("Oak_Stairs[half=top, facing=east]");
        expect("state id normalized", s.id().equals("minecraft:oak_stairs"));
        expect("state props parsed", s.props().get("half").equals("top") && s.props().get("facing").equals("east"));

        if (failures > 0) {
            System.out.println(failures + " FAILED");
            System.exit(1);
        }
        System.out.println("all schematic tests passed");
    }

    private static Schematic load(Path dir, String name, Map<String, Object> root) throws IOException {
        Path f = dir.resolve(name);
        Files.write(f, gzip(root));
        return Schematic.load(f);
    }

    private static void check(String name, Schematic s, String[] palette, int[][][] blocks) {
        int sx = blocks.length, sy = blocks[0].length, sz = blocks[0][0].length;
        Map<String, String> got = new HashMap<>();
        for (Schematic.Entry e : s.blocks) got.put(e.x() + "," + e.y() + "," + e.z(), e.state().toString());
        int expected = 0;
        boolean ok = s.sizeX == sx && s.sizeY == sy && s.sizeZ == sz;
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            String want = palette[blocks[x][y][z]];
            Schematic.StateSpec spec = Schematic.StateSpec.parse(want);
            if (spec.isAir()) continue;
            expected++;
            String have = got.get(x + "," + y + "," + z);
            if (!spec.toString().equals(have)) {
                if (ok) System.out.println("  " + name + ": at " + x + "," + y + "," + z + " want " + spec + " got " + have);
                ok = false;
            }
        }
        ok &= expected == s.blocks.size();
        expect(name + " (" + s.blocks.size() + " blocks)", ok);
    }

    private static void expect(String name, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + name);
        if (!ok) failures++;
    }

    // ---- writers (independent of the reader) ----

    private static Map<String, Object> litematic(String[] palette, int[][][] blocks, boolean negative) {
        int sx = blocks.length, sy = blocks[0].length, sz = blocks[0][0].length;
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        long volume = (long) sx * sy * sz;
        long[] arr = new long[(int) ((volume * bits + 63) / 64)];
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            long index = (long) y * sx * sz + (long) z * sx + x;
            long value = blocks[x][y][z];
            for (int b = 0; b < bits; b++) {
                if (((value >> b) & 1) == 0) continue;
                long bit = index * bits + b;
                arr[(int) (bit >>> 6)] |= 1L << (bit & 63);
            }
        }
        List<Object> pal = new ArrayList<>();
        for (String p : palette) pal.add(stateCompound(p));
        Map<String, Object> region = new LinkedHashMap<>();
        // A negative size means the region extends from Position towards negative coordinates.
        region.put("Position", xyz(negative ? sx - 1 : 0, negative ? sy - 1 : 0, negative ? sz - 1 : 0));
        region.put("Size", negative ? xyz(-sx, -sy, -sz) : xyz(sx, sy, sz));
        region.put("BlockStatePalette", pal);
        region.put("BlockStates", arr);
        Map<String, Object> regions = new LinkedHashMap<>();
        regions.put("main", region);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("Name", "test");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", 6);
        root.put("Metadata", meta);
        root.put("Regions", regions);
        return root;
    }

    private static Map<String, Object> sponge(String[] palette, int[][][] blocks, int version) {
        int sx = blocks.length, sy = blocks[0].length, sz = blocks[0][0].length;
        Map<String, Object> pal = new LinkedHashMap<>();
        for (int i = 0; i < palette.length; i++) pal.put(palette[i], i);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
            int v = blocks[x][y][z];
            while ((v & ~0x7F) != 0) {
                data.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            data.write(v);
        }
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("Version", version);
        s.put("Width", (short) sx);
        s.put("Height", (short) sy);
        s.put("Length", (short) sz);
        if (version >= 3) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("Palette", pal);
            b.put("Data", data.toByteArray());
            s.put("Blocks", b);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("Schematic", s);
            return root;
        }
        s.put("Palette", pal);
        s.put("BlockData", data.toByteArray());
        return s;
    }

    private static Map<String, Object> structure(String[] palette, int[][][] blocks) {
        List<Object> pal = new ArrayList<>();
        for (String p : palette) pal.add(stateCompound(p));
        List<Object> list = new ArrayList<>();
        for (int x = 0; x < blocks.length; x++) for (int y = 0; y < blocks[0].length; y++) for (int z = 0; z < blocks[0][0].length; z++) {
            Map<String, Object> b = new LinkedHashMap<>();
            List<Object> pos = new ArrayList<>(List.of(x, y, z));
            b.put("pos", pos);
            b.put("state", blocks[x][y][z]);
            list.add(b);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("size", new ArrayList<>(List.of(blocks.length, blocks[0].length, blocks[0][0].length)));
        root.put("palette", pal);
        root.put("blocks", list);
        return root;
    }

    private static Map<String, Object> stateCompound(String s) {
        Schematic.StateSpec spec = Schematic.StateSpec.parse(s);
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("Name", spec.id());
        if (!spec.props().isEmpty()) c.put("Properties", new LinkedHashMap<String, Object>(spec.props()));
        return c;
    }

    private static Map<String, Object> xyz(int x, int y, int z) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", x);
        m.put("y", y);
        m.put("z", z);
        return m;
    }

    private static byte[] gzip(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            writePayload(out, root);
        }
        return bytes.toByteArray();
    }

    private static int tagOf(Object o) {
        if (o instanceof Byte) return 1;
        if (o instanceof Short) return 2;
        if (o instanceof Integer) return 3;
        if (o instanceof Long) return 4;
        if (o instanceof byte[]) return 7;
        if (o instanceof String) return 8;
        if (o instanceof List) return 9;
        if (o instanceof Map) return 10;
        if (o instanceof int[]) return 11;
        if (o instanceof long[]) return 12;
        throw new IllegalArgumentException(o.getClass().toString());
    }

    @SuppressWarnings("unchecked")
    private static void writePayload(DataOutputStream out, Object o) throws IOException {
        switch (tagOf(o)) {
            case 1 -> out.writeByte((Byte) o);
            case 2 -> out.writeShort((Short) o);
            case 3 -> out.writeInt((Integer) o);
            case 4 -> out.writeLong((Long) o);
            case 7 -> {
                byte[] a = (byte[]) o;
                out.writeInt(a.length);
                out.write(a);
            }
            case 8 -> out.writeUTF((String) o);
            case 9 -> {
                List<Object> l = (List<Object>) o;
                out.writeByte(l.isEmpty() ? 0 : tagOf(l.get(0)));
                out.writeInt(l.size());
                for (Object e : l) writePayload(out, e);
            }
            case 10 -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    out.writeByte(tagOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    writePayload(out, e.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> {
                int[] a = (int[]) o;
                out.writeInt(a.length);
                for (int v : a) out.writeInt(v);
            }
            case 12 -> {
                long[] a = (long[]) o;
                out.writeInt(a.length);
                for (long v : a) out.writeLong(v);
            }
            default -> throw new IllegalStateException();
        }
    }
}
