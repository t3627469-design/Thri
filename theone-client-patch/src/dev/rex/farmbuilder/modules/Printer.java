package dev.rex.farmbuilder.modules;

import dev.rex.stealth.Spooky;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;

/**
 * Litematica-printer style: the only-build schematic is built from wherever you stand. Each build tick the
 * nearest block in reach that can be placed facing right is looked at (visibly) and placed; when nothing is
 * in reach the bot walks to the next block, as only-build does. Turning this on loads the schematic through
 * only-build's settings (file-name, speed, reach); turning it off stops it.
 */
public class Printer extends Module {
    public Printer() {
        super(Spooky.category(), "printer",
            "Places the only-build schematic: places every block in reach from where you stand, walks to the rest.");
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

    private static OnlyBuild build() {
        Module m = Modules.get().get(OnlyBuild.class);
        return m instanceof OnlyBuild ? (OnlyBuild) m : null;
    }
}
