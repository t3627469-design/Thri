package dev.rex.farmbuilder.modules;

import dev.rex.stealth.Spooky;
import java.lang.reflect.Field;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

/**
 * Litematica-printer style: the only-build schematic is placed from wherever you stand. Every tick the
 * nearest block in reach that can be placed facing right is looked at (visibly) and placed. It never
 * walks or climbs; you do the moving. Turning this on loads the schematic through only-build's settings
 * (file-name, speed, reach); turning it off stops it.
 */
public class Printer extends Module {
    public Printer() {
        super(Spooky.category(), "printer",
            "Places the only-build schematic from where you stand: looks at each reachable block and places it. Never walks; you move.");
    }

    @Override
    public void onActivate() {
        Guard.setPrinting(true);
        OnlyBuild build = build();
        if (build != null && !build.isActive()) {
            build.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        Guard.setPrinting(false);
        OnlyBuild build = build();
        if (build != null && build.isActive()) {
            build.toggle();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post post) {
        OnlyBuild build = build();
        if (build == null || !build.isActive()) {
            return;
        }
        try {
            Field wf = OnlyBuild.class.getDeclaredField("wanted");
            wf.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<class_2338, class_2680> wanted = (Map<class_2338, class_2680>) wf.get(build);
            int speed = ((Number) setting(build, "speed")).intValue();
            double reach = ((Number) setting(build, "reach")).doubleValue();
            Guard.printerTick(wanted, speed, reach, this);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warning("Printer hit a snag (%s).", new Object[]{e.getClass().getSimpleName()});
        }
    }

    private static OnlyBuild build() {
        Module m = Modules.get().get(OnlyBuild.class);
        return m instanceof OnlyBuild ? (OnlyBuild) m : null;
    }

    /** Value of one of only-build's settings (its private Setting field's get()). */
    private static Object setting(OnlyBuild build, String field) throws ReflectiveOperationException {
        Field f = OnlyBuild.class.getDeclaredField(field);
        f.setAccessible(true);
        Object s = f.get(build);
        return s.getClass().getMethod("get").invoke(s);
    }
}
