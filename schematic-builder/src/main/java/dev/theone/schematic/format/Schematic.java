package dev.theone.schematic.format;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A loaded schematic: a flat list of non-air blocks with positions relative to the schematic's
 * minimum corner. Supports Litematica (.litematic), Sponge/WorldEdit (.schem v1-v3) and vanilla
 * structure (.nbt) files.
 */
public final class Schematic {
    /** A block state as written in the file: namespaced id plus property map. */
    public record StateSpec(String id, Map<String, String> props) {
        public static StateSpec parse(String s) {
            int bracket = s.indexOf('[');
            String id = (bracket < 0 ? s : s.substring(0, bracket)).trim();
            Map<String, String> props = new TreeMap<>();
            if (bracket >= 0) {
                int end = s.lastIndexOf(']');
                String body = s.substring(bracket + 1, end < 0 ? s.length() : end);
                for (String part : body.split(",")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) props.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
            return new StateSpec(normalizeId(id), Collections.unmodifiableMap(props));
        }

        public boolean isAir() {
            return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air")
                || id.equals("minecraft:structure_void");
        }

        @Override
        public String toString() {
            if (props.isEmpty()) return id;
            StringBuilder sb = new StringBuilder(id).append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!first) sb.append(',');
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            return sb.append(']').toString();
        }
    }

    /** One block of the schematic, relative to its minimum corner. */
    public record Entry(int x, int y, int z, StateSpec state) {
    }

    public final String name;
    public final int sizeX, sizeY, sizeZ;
    public final List<Entry> blocks;

    public Schematic(String name, int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        this.name = name;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = Collections.unmodifiableList(blocks);
    }

    static String normalizeId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        return lower.indexOf(':') < 0 ? "minecraft:" + lower : lower;
    }

    public static Schematic load(Path file) throws IOException {
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        Map<String, Object> root = Nbt.read(file);
        String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        if (lower.endsWith(".litematic")) return Litematic.parse(base, root);
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) return Sponge.parse(base, root);
        if (lower.endsWith(".nbt")) return Structure.parse(base, root);
        // Unknown extension: sniff the layout.
        if (root.containsKey("Regions")) return Litematic.parse(base, root);
        if (root.containsKey("palette") || root.containsKey("palettes")) return Structure.parse(base, root);
        return Sponge.parse(base, root);
    }

    /** Builds a schematic from collected entries, shifting them so the minimum corner is 0,0,0. */
    static Schematic normalized(String name, List<Entry> raw) throws IOException {
        if (raw.isEmpty()) throw new IOException("Schematic contains no blocks");
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Entry e : raw) {
            minX = Math.min(minX, e.x); minY = Math.min(minY, e.y); minZ = Math.min(minZ, e.z);
            maxX = Math.max(maxX, e.x); maxY = Math.max(maxY, e.y); maxZ = Math.max(maxZ, e.z);
        }
        List<Entry> out = new ArrayList<>(raw.size());
        for (Entry e : raw) out.add(new Entry(e.x - minX, e.y - minY, e.z - minZ, e.state));
        return new Schematic(name, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, out);
    }

    // ---------------------------------------------------------------------------------------------

    /** Litematica v4-v7 format. Regions are merged into one block list. */
    static final class Litematic {
        static Schematic parse(String name, Map<String, Object> root) throws IOException {
            Map<String, Object> regions = Nbt.compound(root, "Regions");
            Map<String, Object> meta = Nbt.optCompound(root, "Metadata");
            if (meta != null && meta.get("Name") instanceof String n && !n.isBlank()) name = n;
            List<Entry> all = new ArrayList<>();
            for (Object regionObj : regions.values()) {
                if (!(regionObj instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> region = (Map<String, Object>) regionObj;
                Map<String, Object> pos = Nbt.compound(region, "Position");
                Map<String, Object> size = Nbt.compound(region, "Size");
                int px = Nbt.integer(pos, "x"), py = Nbt.integer(pos, "y"), pz = Nbt.integer(pos, "z");
                int sx = Nbt.integer(size, "x"), sy = Nbt.integer(size, "y"), sz = Nbt.integer(size, "z");
                // Negative sizes extend from the position towards negative coordinates.
                int minX = sx < 0 ? px + sx + 1 : px;
                int minY = sy < 0 ? py + sy + 1 : py;
                int minZ = sz < 0 ? pz + sz + 1 : pz;
                int ax = Math.abs(sx), ay = Math.abs(sy), az = Math.abs(sz);

                List<Object> paletteList = Nbt.list(region, "BlockStatePalette");
                StateSpec[] palette = new StateSpec[paletteList.size()];
                for (int i = 0; i < palette.length; i++) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> p = (Map<String, Object>) paletteList.get(i);
                    palette[i] = specFromCompound(p);
                }
                Object statesObj = region.get("BlockStates");
                if (!(statesObj instanceof long[] states)) throw new IOException("Region is missing BlockStates");

                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(palette.length - 1, 1)));
                long volume = (long) ax * ay * az;
                if (volume > Integer.MAX_VALUE) throw new IOException("Region too large");
                long mask = (1L << bits) - 1L;
                for (long index = 0; index < volume; index++) {
                    // Litematica packs values contiguously; a value may straddle two longs.
                    long startBit = index * bits;
                    int startLong = (int) (startBit >>> 6);
                    int endLong = (int) (((index + 1) * bits - 1) >>> 6);
                    int offset = (int) (startBit & 63);
                    if (endLong >= states.length) throw new IOException("BlockStates array too short");
                    long value;
                    if (startLong == endLong) {
                        value = (states[startLong] >>> offset) & mask;
                    } else {
                        int endOffset = 64 - offset;
                        value = ((states[startLong] >>> offset) | (states[endLong] << endOffset)) & mask;
                    }
                    if (value >= palette.length) continue;
                    StateSpec spec = palette[(int) value];
                    if (spec.isAir()) continue;
                    int i = (int) index;
                    int x = i % ax;
                    int z = (i / ax) % az;
                    int y = i / (ax * az);
                    all.add(new Entry(minX + x, minY + y, minZ + z, spec));
                }
            }
            return normalized(name, all);
        }
    }

    /** Sponge schematic v1/v2 (root) and v3 (root.Schematic.Blocks). */
    static final class Sponge {
        static Schematic parse(String name, Map<String, Object> root) throws IOException {
            Map<String, Object> s = Nbt.optCompound(root, "Schematic");
            if (s == null) s = root;
            int w = Nbt.integer(s, "Width") & 0xFFFF;
            int h = Nbt.integer(s, "Height") & 0xFFFF;
            int l = Nbt.integer(s, "Length") & 0xFFFF;

            Map<String, Object> paletteTag;
            byte[] data;
            Map<String, Object> blocks = Nbt.optCompound(s, "Blocks");
            if (blocks != null) {
                paletteTag = Nbt.compound(blocks, "Palette");
                data = (byte[]) blocks.get("Data");
            } else {
                if (!s.containsKey("Palette")) {
                    throw new IOException("Legacy MCEdit .schematic (numeric ids) is not supported; re-save it as .schem or .litematic");
                }
                paletteTag = Nbt.compound(s, "Palette");
                data = (byte[]) s.get("BlockData");
            }
            if (data == null) throw new IOException("Schematic has no block data");

            int max = 0;
            for (Object v : paletteTag.values()) max = Math.max(max, ((Number) v).intValue());
            StateSpec[] palette = new StateSpec[max + 1];
            for (Map.Entry<String, Object> e : paletteTag.entrySet()) {
                palette[((Number) e.getValue()).intValue()] = StateSpec.parse(e.getKey());
            }

            List<Entry> out = new ArrayList<>();
            int index = 0;
            int pos = 0;
            long volume = (long) w * h * l;
            while (pos < data.length && index < volume) {
                int value = 0, shift = 0;
                while (true) {
                    if (pos >= data.length) throw new IOException("Truncated varint in block data");
                    int b = data[pos++];
                    value |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) break;
                    shift += 7;
                    if (shift > 28) throw new IOException("Varint too long");
                }
                if (value < palette.length && palette[value] != null && !palette[value].isAir()) {
                    int x = index % w;
                    int z = (index / w) % l;
                    int y = index / (w * l);
                    out.add(new Entry(x, y, z, palette[value]));
                }
                index++;
            }
            return normalized(name, out);
        }
    }

    /** Vanilla structure block format (.nbt). */
    static final class Structure {
        static Schematic parse(String name, Map<String, Object> root) throws IOException {
            List<Object> paletteList;
            if (root.get("palette") instanceof List<?>) {
                paletteList = Nbt.list(root, "palette");
            } else {
                List<Object> palettes = Nbt.list(root, "palettes");
                if (palettes.isEmpty()) throw new IOException("Structure has no palette");
                @SuppressWarnings("unchecked")
                List<Object> first = (List<Object>) palettes.get(0);
                paletteList = first;
            }
            StateSpec[] palette = new StateSpec[paletteList.size()];
            for (int i = 0; i < palette.length; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> p = (Map<String, Object>) paletteList.get(i);
                palette[i] = specFromCompound(p);
            }
            List<Entry> out = new ArrayList<>();
            for (Object o : Nbt.list(root, "blocks")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> b = (Map<String, Object>) o;
                int[] pos = Nbt.ints(b.get("pos"));
                int state = Nbt.integer(b, "state");
                if (state < 0 || state >= palette.length || palette[state].isAir()) continue;
                out.add(new Entry(pos[0], pos[1], pos[2], palette[state]));
            }
            return normalized(name, out);
        }
    }

    static StateSpec specFromCompound(Map<String, Object> tag) throws IOException {
        String id = normalizeId(Nbt.string(tag, "Name"));
        Map<String, String> props = new TreeMap<>();
        Map<String, Object> p = Nbt.optCompound(tag, "Properties");
        if (p != null) for (Map.Entry<String, Object> e : p.entrySet()) props.put(e.getKey(), String.valueOf(e.getValue()));
        return new StateSpec(id, Collections.unmodifiableMap(props));
    }
}
