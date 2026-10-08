package dev.thri.module;

import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final Category category;
    protected int key;
    private boolean enabled;
    public final List<Setting<?>> settings = new ArrayList<>();
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    protected Module(String name, Category category, int key) {
        this.name = name; this.category = category; this.key = key;
    }

    public final boolean isEnabled() { return enabled; }
    public final int getKey() { return key; }
    public final void setKey(int k) { this.key = k; }

    public final void toggle() { setEnabled(!enabled); }
    public final void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (on) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}

    protected <T, S extends Setting<T>> S add(S s) { settings.add(s); return s; }
}
