package dev.rex.farmbuilder.modules;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Farm Builder remembers between runs and restarts: which items a server's AH did not have
 * (so it gathers them instead of re-checking the AH every batch) and a log of recent events.
 * Stored as plain text in the Meteor folder; free of Minecraft classes so it can be tested headlessly.
 */
final class FarmMemory {
   static final int MAX_EVENTS = 60;
   private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm");

   private final Path file;
   private final Map<String, Long> ahMisses = new LinkedHashMap<>();
   private final Deque<String> events = new ArrayDeque<>();
   private boolean loaded;

   FarmMemory(Path file) {
      this.file = file;
   }

   private static String key(String server, String item) {
      return (server == null ? "" : server.trim().toLowerCase(java.util.Locale.ROOT)) + "|" + item;
   }

   void ahMissed(String server, String item, long nowMs) {
      this.load();
      this.ahMisses.remove(key(server, item));
      this.ahMisses.put(key(server, item), nowMs);
      this.save();
   }

   boolean recentlyMissed(String server, String item, long nowMs, long ttlMs) {
      this.load();
      Long at = this.ahMisses.get(key(server, item));
      return at != null && ttlMs > 0 && nowMs - at >= 0 && nowMs - at < ttlMs;
   }

   /** Items this server's AH lacked within the last ttl, newest first, with minutes since the miss. */
   List<String> recentMisses(String server, long nowMs, long ttlMs) {
      this.load();
      String prefix = key(server, "");
      List<String> out = new ArrayList<>();
      for (Map.Entry<String, Long> e : this.ahMisses.entrySet()) {
         long age = nowMs - e.getValue();
         if (e.getKey().startsWith(prefix) && age >= 0 && age < ttlMs) {
            out.add(0, e.getKey().substring(prefix.length()) + " (" + age / 60000 + " min ago)");
         }
      }
      return out;
   }

   void forgetAhMisses() {
      this.load();
      this.ahMisses.clear();
      this.save();
   }

   void event(String text, long nowMs) {
      this.load();
      String stamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault()).format(STAMP);
      this.events.addLast(stamp + "  " + text.replace('\n', ' ').replace('\t', ' '));
      while (this.events.size() > MAX_EVENTS) this.events.removeFirst();
      this.save();
   }

   /** Most recent events, newest first. */
   List<String> recentEvents(int max) {
      this.load();
      List<String> out = new ArrayList<>(this.events);
      java.util.Collections.reverse(out);
      return out.size() > max ? out.subList(0, max) : out;
   }

   private void load() {
      if (this.loaded) return;
      this.loaded = true;
      try {
         if (!Files.isRegularFile(this.file) || Files.size(this.file) > 1 << 20) return;
         for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            String[] p = line.split("\t", 3);
            if (p.length == 3 && p[0].equals("miss")) {
               try {
                  this.ahMisses.put(p[1], Long.parseLong(p[2]));
               } catch (NumberFormatException ignored) {
               }
            } else if (p.length >= 2 && p[0].equals("event")) {
               this.events.addLast(line.substring("event\t".length()));
            }
         }
         while (this.events.size() > MAX_EVENTS) this.events.removeFirst();
      } catch (Exception ignored) {
      }
   }

   private void save() {
      StringBuilder sb = new StringBuilder("# Farm Builder memory. Safe to delete; it is rebuilt as the bot runs.\n");
      long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
      for (Map.Entry<String, Long> e : this.ahMisses.entrySet()) {
         if (e.getValue() >= cutoff) sb.append("miss\t").append(e.getKey()).append('\t').append(e.getValue()).append('\n');
      }
      for (String ev : this.events) sb.append("event\t").append(ev).append('\n');
      try {
         FarmFiles.writeAtomic(this.file, sb.toString());
      } catch (Exception ignored) {
      }
   }
}
