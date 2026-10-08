package dev.theone.schematic.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Copies schematics shipped inside the jar into .minecraft/schematics on startup, so they show up
 * for the builder without any manual copying. Files the player already has are never overwritten.
 * The build script writes the index from the contents of the bundled schematics folder.
 */
public final class BundledSchematics {
    private static final String ROOT = "/assets/theone-client/schematics/";

    private BundledSchematics() {
    }

    public static void install() {
        try (InputStream index = BundledSchematics.class.getResourceAsStream(ROOT + "index.txt")) {
            if (index == null) return;
            Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
            Files.createDirectories(dir);
            BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8));
            String name;
            while ((name = reader.readLine()) != null) {
                name = name.trim();
                if (name.isEmpty() || name.contains("/") || name.contains("\\")) continue;
                Path target = dir.resolve(name);
                if (Files.exists(target)) continue;
                try (InputStream in = BundledSchematics.class.getResourceAsStream(ROOT + name)) {
                    if (in != null) Files.copy(in, target);
                }
            }
        } catch (IOException e) {
            System.out.println("[TheOne] could not install bundled schematics: " + e);
        }
    }
}
