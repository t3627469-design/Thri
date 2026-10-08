package dev.theone.schematic;

import dev.theone.schematic.util.BundledSchematics;
import dev.theone.schematic.util.Mc;
import dev.theone.schematic.modules.SchematicBuilder;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class TheOneAddon extends MeteorAddon {
    public static final Category CATEGORY = new Category("TheOne", Mc.NETHER_STAR.getDefaultStack());

    @Override
    public void onInitialize() {
        BundledSchematics.install();
        Modules.get().add(new SchematicBuilder());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.theone.schematic";
    }
}
