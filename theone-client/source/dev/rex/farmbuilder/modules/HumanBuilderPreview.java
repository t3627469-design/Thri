package dev.rex.farmbuilder.modules;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_2338;
import net.minecraft.class_746;

/** Thin outline of the blocks still to place in the layer being built, near you: the fallback when Litematica is not installed. Lines only, so it never washes out the screen. */
public final class HumanBuilderPreview {
   private static final int RANGE = 20;
   private static final int MAX_BOXES = 700;
   private static final Color SIDE = new Color(60, 255, 90, 0);
   private static final Color LINE = new Color(60, 255, 90, 130);

   /** Set while Litematica draws the build itself, so the simple outline stays off. */
   static volatile boolean suppress;

   private static final String[] NAMES = {"dev.rex.farmbuilder.modules.HumanBuilder", "dev.rex.farmbuilder.modules.OnlyBuild"};
   private final Module[] modules = new Module[NAMES.length];
   private final Method[] listers = new Method[NAMES.length];
   private boolean ready;
   private boolean failed;

   public static void register() {
      try {
         MeteorClient.EVENT_BUS.subscribe(new HumanBuilderPreview());
      } catch (Throwable t) {
         System.out.println("[Farm Builder] build preview unavailable: " + t);
      }
   }

   @EventHandler
   public void onRender(Render3DEvent event) {
      if (this.failed || suppress) return;
      try {
         if (!this.ready) {
            for (int i = 0; i < NAMES.length; i++) {
               Class<?> cls = Class.forName(NAMES[i]);
               this.modules[i] = Modules.get().get((Class) cls);
               this.listers[i] = cls.getDeclaredMethod("previewList");
               this.listers[i].setAccessible(true);
            }
            this.ready = true;
         }
         class_746 player = MeteorClient.mc.field_1724;
         if (player == null) return;
         class_2338 here = player.method_24515();
         for (int m = 0; m < NAMES.length; m++) {
            if (this.modules[m] == null || !this.modules[m].isActive()) continue;
            List<?> todo = new ArrayList<>((List<?>) this.listers[m].invoke(this.modules[m]));
            int drawn = 0;
            for (Object o : todo) {
               class_2338 p = (class_2338) o;
               if (Math.abs(p.method_10263() - here.method_10263()) > RANGE
                  || Math.abs(p.method_10260() - here.method_10260()) > RANGE
                  || Math.abs(p.method_10264() - here.method_10264()) > RANGE) continue;
               event.renderer.box(p, SIDE, LINE, ShapeMode.Lines, 0);
               if (++drawn >= MAX_BOXES) break;
            }
         }
      } catch (Throwable t) {
         this.failed = true;
         System.out.println("[Farm Builder] build preview disabled: " + t);
      }
   }
}
