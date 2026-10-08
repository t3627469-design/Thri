package dev.rex.farmbuilder.modules;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.orbit.EventHandler;

/**
 * Keeps every Farm Builder setting in its own file, saved within two seconds of any change and
 * restored at startup, so the setup survives crashes and is the same on every server.
 */
final class SettingsVault {
   private final Settings settings;
   private final File file;
   private boolean restored;
   private int ticks;
   private String last;

   SettingsVault(Settings settings, File file) {
      this.settings = settings;
      this.file = file;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!this.restored) {
         this.restored = true;
         this.restore();
         this.last = this.snapshot();
         return;
      }
      if (++this.ticks % 40 != 0) return;
      this.saveIfChanged();
   }

   void saveIfChanged() {
      if (!this.restored) return;
      String now = this.snapshot();
      if (now.equals(this.last)) return;
      this.last = now;
      try {
         FarmFiles.writeAtomic(this.file.toPath(), now);
      } catch (Exception ignored) {
      }
   }

   private static boolean simple(Object v) {
      return v instanceof Boolean || v instanceof Integer || v instanceof Double || v instanceof Enum<?> || v instanceof String;
   }

   private static String esc(String s) {
      return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t");
   }

   private static String unesc(String s) {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         if (c == '\\' && i + 1 < s.length()) {
            char n = s.charAt(++i);
            b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
         } else b.append(c);
      }
      return b.toString();
   }

   private String snapshot() {
      StringBuilder sb = new StringBuilder("# Farm Builder settings, saved automatically and used on every server.\n");
      for (SettingGroup g : this.settings) {
         for (Setting<?> s : g) {
            Object v = s.get();
            if (simple(v)) sb.append(s.name).append('\t').append(esc(v.toString())).append('\n');
         }
      }
      return sb.toString();
   }

   private void restore() {
      // Keep the page short: only the farm-choice section starts open, the rest is one click away.
      for (SettingGroup g : this.settings) g.sectionExpanded = g.get("farm") != null;
      try {
         if (!this.file.isFile()) return;
         Map<String, String> saved = new HashMap<>();
         for (String line : Files.readAllLines(this.file.toPath(), StandardCharsets.UTF_8)) {
            int tab = line.indexOf('\t');
            if (tab > 0 && !line.startsWith("#")) saved.put(line.substring(0, tab), unesc(line.substring(tab + 1)));
         }
         for (SettingGroup g : this.settings) {
            for (Setting<?> s : g) {
               String v = saved.get(s.name);
               Object cur = s.get();
               if (v != null && simple(cur) && !v.equals(cur.toString())) {
                  try {
                     s.parse(v);
                  } catch (Exception ignored) {
                  }
               }
            }
         }
      } catch (Exception ignored) {
      }
   }
}
