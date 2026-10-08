package dev.thri.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.thri.Thri;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("thri.json");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void load() {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (Module m : Thri.MODULES.all()) {
                if (!root.has(m.name)) continue;
                JsonObject mj = root.getAsJsonObject(m.name);
                if (mj.has("enabled") && mj.get("enabled").getAsBoolean()) m.setEnabled(true);
                if (mj.has("key")) m.setKey(mj.get("key").getAsInt());
                if (mj.has("settings")) {
                    JsonObject sj = mj.getAsJsonObject("settings");
                    for (Setting<?> s : m.settings) {
                        if (!sj.has(s.name)) continue;
                        JsonElement el = sj.get(s.name);
                        if (el.isJsonPrimitive()) s.setFromObject(primitiveToJava(el));
                    }
                }
            }
        } catch (Exception e) { Thri.LOG.warn("config load", e); }
    }

    public void save() {
        try {
            JsonObject root = new JsonObject();
            for (Module m : Thri.MODULES.all()) {
                JsonObject mj = new JsonObject();
                mj.addProperty("enabled", m.isEnabled());
                mj.addProperty("key", m.getKey());
                JsonObject sj = new JsonObject();
                for (Setting<?> s : m.settings) {
                    Object v = s.get();
                    if (v instanceof Boolean b) sj.addProperty(s.name, b);
                    else if (v instanceof Number n) sj.addProperty(s.name, n);
                    else sj.addProperty(s.name, v.toString());
                }
                mj.add("settings", sj);
                root.add(m.name, mj);
            }
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(root));
        } catch (IOException e) { Thri.LOG.warn("config save", e); }
    }

    private Object primitiveToJava(JsonElement el) {
        var p = el.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isNumber())  return p.getAsNumber();
        return p.getAsString();
    }
}
