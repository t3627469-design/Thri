package dev.rex.farmbuilder.modules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.class_2338;

record FarmLocation(class_2338 origin, String dimension, String server) {
    FarmLocation {
        if (origin == null || Math.abs((long) origin.method_10263()) > 29999984
            || Math.abs((long) origin.method_10260()) > 29999984
            || origin.method_10264() < -2032 || origin.method_10264() > 2031)
            throw new IllegalArgumentException("Build location is outside Minecraft's supported coordinates.");
        if (dimension == null || !dimension.matches("[a-z0-9._-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("Build location has an invalid dimension.");
        origin = new class_2338(origin.method_10263(), origin.method_10264(), origin.method_10260());
        server = server == null ? "" : server.trim().toLowerCase(java.util.Locale.ROOT);
    }

    boolean matches(String currentDimension, String currentServer) {
        return dimension.equals(currentDimension) && (server.isEmpty() || server.equals(
            currentServer == null ? "" : currentServer.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    static class_2338 above(class_2338 surface) {
        if (surface == null || surface.method_10264() == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Aim at a ground block first.");
        return new class_2338(surface.method_10263(), surface.method_10264() + 1, surface.method_10260());
    }

    boolean fits(int width, int height, int length, int bottom, int worldHeight) {
        return width > 0 && height > 0 && length > 0
            && origin.method_10264() >= bottom
            && (long) origin.method_10264() + height <= (long) bottom + worldHeight
            && (long) origin.method_10263() + width - 1 <= 29999984
            && (long) origin.method_10260() + length - 1 <= 29999984;
    }

    String encode() {
        return "version=1\nx=" + origin.method_10263() + "\ny=" + origin.method_10264()
            + "\nz=" + origin.method_10260() + "\ndim=" + dimension + "\nserver="
            + Base64.getEncoder().encodeToString(server.getBytes(StandardCharsets.UTF_8)) + "\n";
    }

    static FarmLocation decode(String text) {
        if (text == null || text.length() > 16384) throw new IllegalArgumentException("Invalid saved build location.");
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\\R")) {
            int equals = line.indexOf('=');
            if (equals > 0 && values.put(line.substring(0, equals), line.substring(equals + 1)) != null)
                throw new IllegalArgumentException("Duplicate build location field.");
        }
        if (!"1".equals(values.get("version"))) throw new IllegalArgumentException("Unsupported build location version.");
        return new FarmLocation(new class_2338(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")),
            Integer.parseInt(values.get("z"))), values.get("dim"), new String(Base64.getDecoder().decode(
            values.getOrDefault("server", "")), StandardCharsets.UTF_8));
    }

    void save(Path path) throws Exception { FarmFiles.writeAtomic(path, encode()); }

    static FarmLocation read(Path path) throws java.io.IOException {
        if (Files.size(path) > 16384) throw new java.io.IOException("Saved location file is too large.");
        return decode(Files.readString(path, StandardCharsets.UTF_8));
    }
}
