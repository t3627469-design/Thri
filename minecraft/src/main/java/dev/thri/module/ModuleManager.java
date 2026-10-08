package dev.thri.module;

import dev.thri.module.modules.combat.*;
import dev.thri.module.modules.movement.*;
import dev.thri.module.modules.player.*;
import dev.thri.module.modules.render.*;
import dev.thri.module.modules.world.*;
import dev.thri.module.modules.misc.*;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager() {
        // Combat
        add(new KillAura());
        add(new Aimbot());
        add(new AutoTotem());
        add(new Reach());
        add(new Velocity());
        add(new AntiKnockback());
        add(new Criticals());
        // Movement
        add(new Fly());
        add(new Speed());
        add(new NoFall());
        add(new Sprint());
        add(new Jesus());
        add(new Freecam());
        add(new Step());
        // World
        add(new Scaffold());
        add(new Nuker());
        add(new FastBreak());
        add(new XRay());
        // Render
        add(new ESP());
        add(new Tracers());
        add(new Fullbright());
        add(new Chams());
        add(new HUD());
        // Player
        add(new ChestStealer());
        add(new AutoArmor());
        add(new NoFog());
        // Misc
        add(new ClickGuiModule());
    }

    private void add(Module m) { modules.add(m); }

    public List<Module> all() { return modules; }

    @SuppressWarnings("unchecked")
    public <T extends Module> T get(Class<T> cls) {
        for (Module m : modules) if (cls.isInstance(m)) return (T) m;
        return null;
    }

    public Module byName(String n) {
        for (Module m : modules) if (m.name.equalsIgnoreCase(n)) return m;
        return null;
    }

    public void onTick() {
        for (Module m : modules) if (m.isEnabled()) {
            try { m.onTick(); } catch (Throwable t) { dev.thri.Thri.LOG.error("tick " + m.name, t); }
        }
    }

    public void onKey(int key) {
        if (key <= 0) return;
        for (Module m : modules) if (m.getKey() == key) m.toggle();
    }
}
