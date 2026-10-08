package dev.rex.farmbuilder.modules;

import java.util.ArrayList;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import net.minecraft.class_2248;

final class ManualXray {
   static void prepare() {
      Xray xray = Modules.get().get(Xray.class);
      if (xray == null) return;
      configure(xray.settings.get("blocks"), xray.settings.get("exposed-only"));
   }

   @SuppressWarnings("unchecked")
   static void configure(Setting<?> blocks, Setting<?> exposed) {
      if (blocks != null) ((Setting<java.util.List<class_2248>>) blocks).set(new ArrayList<>(Xray.ORES));
      if (exposed != null) ((Setting<Boolean>) exposed).set(false);
   }
}
