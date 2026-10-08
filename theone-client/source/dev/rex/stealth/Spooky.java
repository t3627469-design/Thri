package dev.rex.stealth;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.orbit.EventHandler;

/**
 * TheOne look for Meteor: a "TheOne" module category and a "TheOne" GUI theme.
 *
 * Meteor's category and theme classes are not used anywhere else in this addon, so their
 * signatures in your Meteor build are unverified. Everything here goes through reflection
 * and degrades to "no change" instead of crashing: the modules then sit in World and the
 * theme list just lacks Halloween.
 */
public final class Spooky {
   static final String THEME = "TheOne";
   private static Category category;
   private static boolean themeRegistered;

   private Spooky() {
   }

   public static Category category() {
      return category != null ? category : Categories.World;
   }

   public static void registerCategory() {
      try {
         Object icon = itemStack("nether_star");
         Category best = null;
         int bestParams = -1;
         for (Constructor<?> c : Category.class.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 0 || p[0] != String.class) continue;
            Object[] args = new Object[p.length];
            args[0] = "TheOne";
            boolean ok = true;
            for (int i = 1; i < p.length && ok; i++) {
               if (icon != null && p[i].isInstance(icon)) args[i] = icon;
               else if (icon != null && p[i] == Supplier.class) {
                  Object fixed = icon;
                  args[i] = (Supplier<Object>) () -> fixed;
               } else if (p[i] == int.class) args[i] = 0x00E0FF;
               else ok = false;
            }
            if (ok && p.length > bestParams) {
               best = (Category) c.newInstance(args);
               bestParams = p.length;
            }
         }
         if (best == null) return;
         Class<?> modules = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
         modules.getMethod("registerCategory", Category.class).invoke(null, best);
         category = best;
      } catch (Throwable t) {
         category = null;
         System.out.println("[TheOne] category unavailable, using World: " + t);
      }
   }

   private static Object itemStack(String id) {
      try {
         Class<?> items = Class.forName("net.minecraft.class_1802");
         Object item = null;
         for (Field f : items.getFields()) {
            if (!Modifier.isStatic(f.getModifiers())) continue;
            Object v = f.get(null);
            if (v != null && String.valueOf(v).endsWith(id)) {
               item = v;
               break;
            }
         }
         if (item == null) return null;
         Class<?> stack = Class.forName("net.minecraft.class_1799");
         for (Constructor<?> c : stack.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length == 1 && p[0].isInstance(item)) return c.newInstance(item);
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   // Halloween palette keyed by Meteor's theme setting names (r, g, b, a).
   private static final Map<String, int[]> PALETTE = new HashMap<>();

   static {
      // TheOne: near-black glass with a single cyan accent and a violet secondary.
      put("accent-color", 0, 224, 255, 255);
      put("checkbox-color", 0, 224, 255, 255);
      put("plus-color", 60, 240, 160, 255);
      put("minus-color", 255, 82, 102, 255);
      put("favorite-color", 255, 210, 90, 255);
      put("text-color", 232, 236, 246, 255);
      put("text-secondary-text-color", 122, 130, 156, 255);
      put("text-highlight-color", 0, 224, 255, 90);
      put("title-text-color", 0, 224, 255, 255);
      put("logged-in-text-color", 60, 240, 160, 255);
      put("placeholder-color", 232, 236, 246, 28);
      put("background-color", 9, 10, 16, 238);
      put("hovered-background-color", 18, 20, 32, 242);
      put("pressed-background-color", 26, 30, 46, 246);
      put("module-background-color", 17, 19, 31, 255);
      put("outline-color", 32, 36, 56, 255);
      put("hovered-outline-color", 0, 224, 255, 150);
      put("pressed-outline-color", 0, 224, 255, 235);
      put("separator-text-color", 0, 224, 255, 255);
      put("separator-center-color", 0, 224, 255, 255);
      put("separator-edges-color", 155, 107, 255, 120);
      put("scrollbar-color", 0, 224, 255, 70);
      put("hovered-scrollbar-color", 0, 224, 255, 130);
      put("pressed-scrollbar-color", 0, 224, 255, 200);
      put("slider-handle-color", 0, 224, 255, 255);
      put("hovered-slider-handle-color", 90, 238, 255, 255);
      put("pressed-slider-handle-color", 155, 107, 255, 255);
      put("slider-left-color", 0, 150, 190, 255);
      put("slider-right-color", 30, 34, 52, 255);
   }

   // Shape and layout settings, applied only when the Meteor build has a setting with that name.
   private static final Map<String, Object> STYLE = new HashMap<>();

   static {
      STYLE.put("category-icons", Boolean.TRUE);
      STYLE.put("round-amount", 7);
      STYLE.put("rounding", 7);
      STYLE.put("corner-radius", 7);
      STYLE.put("blur", Boolean.TRUE);
   }

   private static void put(String name, int r, int g, int b, int a) {
      PALETTE.put(name, new int[]{r, g, b, a});
   }

   /** Adds a "TheOne" copy of Meteor's own theme. Must run before Meteor's post-init loads the saved theme. */
   public static void registerTheme() {
      try {
         Class<?> themes = Class.forName("meteordevelopment.meteorclient.gui.GuiThemes");
         Class<?> guiTheme = Class.forName("meteordevelopment.meteorclient.gui.GuiTheme");
         Class<?> meteorTheme = Class.forName("meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme");
         Object theme = meteorTheme.getConstructor().newInstance();
         // GuiThemes.add replaces any theme with the same name, so the copy must be renamed
         // before it is added or it would overwrite the user's own "Meteor" theme.
         Field name = guiTheme.getField("name");
         name.setAccessible(true);
         name.set(theme, THEME);
         if (!THEME.equals(name.get(theme))) return;
         int recolored = 0;
         Object settings = guiTheme.getField("settings").get(theme);
         for (Object group : (Iterable<?>) settings) {
            for (Object setting : (Iterable<?>) group) {
               if (recolor(setting)) recolored++;
               else applyStyle(setting);
            }
         }
         if (recolored == 0) return;
         themes.getMethod("add", guiTheme).invoke(null, theme);
         themeRegistered = true;
         MeteorClient.EVENT_BUS.subscribe(new FirstJoin());
      } catch (Throwable t) {
         System.out.println("[TheOne] theme unavailable: " + t);
      }
   }

   /** Sets a style setting if this Meteor build has it; every setting name is logged so the list can be tuned. */
   private static void applyStyle(Object setting) {
      try {
         String name = String.valueOf(setting.getClass().getField("name").get(setting));
         Object value = STYLE.get(name.toLowerCase(Locale.ROOT));
         System.out.println("[TheOne] theme setting: " + name + (value == null ? "" : " -> " + value));
         if (value == null) return;
         Object current = setting.getClass().getMethod("get").invoke(setting);
         if (current != null && current.getClass() != value.getClass()) return;
         setting.getClass().getMethod("set", Object.class).invoke(setting, value);
      } catch (Throwable ignored) {
      }
   }

   private static boolean recolor(Object setting) throws ReflectiveOperationException {
      Object rawName = setting.getClass().getField("name").get(setting);
      int[] c = PALETTE.get(String.valueOf(rawName).toLowerCase(Locale.ROOT));
      if (c == null) return false;
      Object def = setting.getClass().getMethod("getDefaultValue").invoke(setting);
      if (def == null) return false;
      Method set;
      try {
         set = def.getClass().getMethod("set", int.class, int.class, int.class, int.class);
      } catch (NoSuchMethodException e) {
         return false;
      }
      set.invoke(def, c[0], c[1], c[2], c[3]);
      setting.getClass().getMethod("reset").invoke(setting);
      return true;
   }

   /** Switches Meteor to the Halloween theme once, on the first world join after install. */
   public static final class FirstJoin {
      private boolean done;

      @EventHandler
      private void onTick(TickEvent.Post event) {
         if (this.done) return;
         this.done = true;
         if (!themeRegistered) return;
         File marker = new File(MeteorClient.FOLDER, "theone-client-theme");
         if (marker.exists()) return;
         try {
            Class.forName("meteordevelopment.meteorclient.gui.GuiThemes").getMethod("select", String.class).invoke(null, THEME);
            marker.getParentFile().mkdirs();
            marker.createNewFile();
         } catch (Throwable t) {
            System.out.println("[TheOne] could not switch to the TheOne theme: " + t);
         }
      }
   }
}
