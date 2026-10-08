package dev.theone.schematic.format;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Minimal standalone NBT reader. Compounds become {@code Map<String, Object>}, lists become
 * {@code List<Object>}, arrays stay primitive arrays and numbers keep their Java boxed type.
 * It does not depend on Minecraft classes so schematic parsing can be unit tested off-game.
 */
public final class Nbt {
    private static final int MAX_DEPTH = 512;

    private Nbt() {
    }

    /** Reads a (usually gzipped) NBT file and returns the root compound. */
    public static Map<String, Object> read(Path file) throws IOException {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(file))) {
            return read(raw);
        }
    }

    public static Map<String, Object> read(InputStream raw) throws IOException {
        PushbackInputStream in = new PushbackInputStream(raw, 2);
        int b0 = in.read();
        int b1 = in.read();
        if (b1 >= 0) in.unread(b1);
        if (b0 >= 0) in.unread(b0);
        InputStream body = (b0 == 0x1f && b1 == 0x8b) ? new GZIPInputStream(in) : in;
        DataInputStream data = new DataInputStream(new BufferedInputStream(body));

        int type = data.readUnsignedByte();
        if (type != 10) throw new IOException("NBT root is not a compound (tag " + type + ")");
        data.readUTF();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) readPayload(data, type, 0);
        return root;
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] a = new byte[checkedLength(in.readInt())];
                in.readFully(a);
                return a;
            }
            case 8: return in.readUTF();
            case 9: {
                int elemType = in.readUnsignedByte();
                int len = checkedLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(len, 4096));
                for (int i = 0; i < len; i++) list.add(readPayload(in, elemType, depth + 1));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int t = in.readUnsignedByte();
                    if (t == 0) break;
                    String name = in.readUTF();
                    map.put(name, readPayload(in, t, depth + 1));
                }
                return map;
            }
            case 11: {
                int[] a = new int[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                return a;
            }
            case 12: {
                long[] a = new long[checkedLength(in.readInt())];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                return a;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }

    private static int checkedLength(int len) throws IOException {
        if (len < 0 || len > 64 * 1024 * 1024) throw new IOException("Bad NBT array length " + len);
        return len;
    }

    // ---- typed accessors ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> parent, String key) throws IOException {
        Object o = parent.get(key);
        if (!(o instanceof Map)) throw new IOException("Missing compound '" + key + "'");
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> optCompound(Map<String, Object> parent, String key) {
        Object o = parent.get(key);
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> parent, String key) throws IOException {
        Object o = parent.get(key);
        if (!(o instanceof List)) throw new IOException("Missing list '" + key + "'");
        return (List<Object>) o;
    }

    public static int integer(Map<String, Object> parent, String key) throws IOException {
        Object o = parent.get(key);
        if (!(o instanceof Number n)) throw new IOException("Missing number '" + key + "'");
        return n.intValue();
    }

    public static int optInt(Map<String, Object> parent, String key, int def) {
        Object o = parent.get(key);
        return o instanceof Number n ? n.intValue() : def;
    }

    public static String string(Map<String, Object> parent, String key) throws IOException {
        Object o = parent.get(key);
        if (!(o instanceof String s)) throw new IOException("Missing string '" + key + "'");
        return s;
    }

    public static int[] ints(Object o) throws IOException {
        if (o instanceof int[] a) return a;
        if (o instanceof List<?> l) {
            int[] a = new int[l.size()];
            for (int i = 0; i < a.length; i++) {
                if (!(l.get(i) instanceof Number n)) throw new IOException("Expected number list");
                a[i] = n.intValue();
            }
            return a;
        }
        throw new IOException("Expected int array");
    }
}
