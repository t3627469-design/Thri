package dev.rex.farmbuilder;

import dev.rex.farmbuilder.modules.FarmBuilder;
import dev.rex.farmbuilder.modules.HumanBuilder;
import dev.rex.farmbuilder.modules.HumanBuilderPreview;
import dev.rex.farmbuilder.modules.OnlyBuild;
import dev.rex.stealth.Spooky;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;

public class FarmBuilderAddon extends MeteorAddon {
   @Override
   public void onRegisterCategories() {
      Spooky.registerCategory();
   }

   @Override
   public void onInitialize() {
      HumanBuilderPreview.register();
      Spooky.registerTheme();
      Modules.get().add(new FarmBuilder());
      Modules.get().add(new HumanBuilder());
      Modules.get().add(new OnlyBuild());
   }

   // Meteor registers event-handler lookup for this package prefix; it must cover
   // both dev.rex.farmbuilder and dev.rex.stealth.
   @Override
   public String getPackage() {
      return "dev.rex";
   }
}
