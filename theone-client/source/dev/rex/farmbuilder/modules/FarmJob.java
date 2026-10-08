package dev.rex.farmbuilder.modules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_2338;

final class FarmJob {
    static final int MAX_BYTES = 262144;
    static Map<String, String> parse(String text) {
        if (text == null || text.length() > MAX_BYTES) throw new IllegalArgumentException("Job file is too large.");
        Map<String, String> values = new HashMap<>();
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            int equals = line.indexOf('=');
            if (equals < 1 || values.put(line.substring(0, equals), line.substring(equals + 1)) != null)
                throw new IllegalArgumentException("Malformed or duplicate job field.");
        }
        if (values.containsKey("version") && !values.get("version").equals("4")) throw new IllegalArgumentException("Unsupported job version.");
        if (!Set.of("FULL", "ITEMS", "GEAR").contains(values.getOrDefault("mode", ""))) throw new IllegalArgumentException("Invalid job mode.");
        if (!Set.of("SweetBerries", "Custom").contains(values.getOrDefault("farm", ""))) throw new IllegalArgumentException("Invalid farm.");
        String dimension = values.getOrDefault("dim", "");
        new FarmLocation(new class_2338(Integer.parseInt(values.get("x")), Integer.parseInt(values.get("y")), Integer.parseInt(values.get("z"))),
            dimension.isEmpty() ? "minecraft:overworld" : dimension, "");
        for (String key : Set.of("running", "homeSet", "gearAhDone", "safeBuild", "craftBatch", "gearPreparing", "storageCrafting", "craftRepair", "stockpiled")) {
            if (values.containsKey(key) && !Set.of("true", "false").contains(values.get(key))) throw new IllegalArgumentException("Invalid job flag: " + key);
        }
        double spent = Double.parseDouble(values.getOrDefault("spent", "0"));
        if (!Double.isFinite(spent) || spent < 0) throw new IllegalArgumentException("Invalid job budget.");
        for (String key : Set.of("url", "file", "server")) decode(values.getOrDefault(key, ""));
        for (String key : Set.of("stash", "depot")) {
            for (String position : values.getOrDefault(key, "").split(";")) {
                if (position.isBlank()) continue;
                String[] coords = position.split(",", -1);
                if (coords.length != 3) throw new IllegalArgumentException("Invalid storage position.");
                new FarmLocation(new class_2338(Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2])),
                    dimension.isEmpty() ? "minecraft:overworld" : dimension, "");
            }
        }
        goals(values.getOrDefault("craftGoals", ""));
        Map<String, Integer> interrupted = goals(values.getOrDefault("interruptedCraftGoals", ""));
        if (!interrupted.isEmpty() && (!Set.of("Auto", "Legit", "XRay").contains(values.getOrDefault("interruptedCraftOreMode", ""))
            || Integer.parseInt(values.getOrDefault("interruptedCraftStall", "0")) < 1
            || Integer.parseInt(values.getOrDefault("interruptedCraftStall", "0")) > 3600)) throw new IllegalArgumentException("Invalid interrupted craft job.");
        return values;
    }

    static Map<String, Integer> goals(String encoded) {
        Map<String, Integer> result = new java.util.LinkedHashMap<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String entry : encoded.split(";")) {
            int equals = entry.indexOf('=');
            if (equals < 1) throw new IllegalArgumentException("Invalid crafting goal.");
            String id = entry.substring(0, equals);
            int count = Integer.parseInt(entry.substring(equals + 1));
            if (!id.matches("[a-z0-9._-]+:[a-z0-9/._-]+") || count < 1 || count > 4096 || result.put(id, count) != null)
                throw new IllegalArgumentException("Invalid or repeated crafting goal.");
        }
        return result;
    }

    static String decode(String text) { return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }

    static Map<String, String> read(Path path) throws java.io.IOException {
        if (Files.size(path) > MAX_BYTES) throw new java.io.IOException("Job file is too large.");
        return parse(Files.readString(path, StandardCharsets.UTF_8));
    }

    static Map<String, String> recover(Path primary, Path backup) throws java.io.IOException {
        try { return read(primary); }
        catch (java.io.IOException | IllegalArgumentException primaryFailure) {
            try { return read(backup); }
            catch (java.io.IOException | IllegalArgumentException backupFailure) {
                throw new java.io.IOException("Neither saved job nor backup is readable.", primaryFailure);
            }
        }
    }

    static void preserve(Path primary, Path backup) throws Exception {
        if (!Files.isRegularFile(primary)) return;
        try {
            read(primary);
        } catch (java.io.IOException | IllegalArgumentException invalid) { return; }
        FarmFiles.writeAtomic(backup, Files.readString(primary, StandardCharsets.UTF_8));
    }
}
