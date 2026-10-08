package dev.rex.farmbuilder.modules;

import it.unimi.dsi.fastutil.Pair;
import java.util.Locale;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.class_339;
import net.minecraft.class_364;
import net.minecraft.class_412;
import net.minecraft.class_419;
import net.minecraft.class_442;
import net.minecraft.class_639;
import net.minecraft.class_642;

final class Reconnect {
   private static final String[] BAN = new String[]{"banned", "blacklist", "permanently", "you are not whitelisted", "suspended"};
   private static final String[] SUSPICIOUS = new String[]{
      "fly",
      "illegal",
      "invalid",
      "cheat",
      "hack",
      "too many packets",
      "spam",
      "movement",
      "speed",
      "reach",
      "kicked by an operator",
      "unfair advantage",
      "anticheat",
      "anti-cheat"
   };
   private final Setting<Boolean> enabled;
   private final Setting<Integer> delay;
   private final Setting<Boolean> instant;
   private final Setting<Integer> maxDelay;
   private final Setting<Integer> maxAttempts;
   private final Setting<Integer> kickLoopLimit;
   private final Setting<Integer> kickCooldown;
   private final java.util.Deque<Long> disconnects = new java.util.ArrayDeque<>();
   private boolean armed;
   private boolean onScreen;
   private int countdown;
   private int attempts;
   private int connectedTicks;
   private boolean awaitingRejoin;
   private boolean rejoined;
   private boolean suspicious;
   private String reason = "";
   private boolean meteorWasOn;

   Reconnect(Settings settings) {
      SettingGroup sg = settings.createGroup("Reconnect");
      this.enabled = sg.add(
         new Builder().name("auto-reconnect").description("Rejoin after a kick or a dropped connection and carry on.").defaultValue(true).build()
      );
      this.instant = sg.add(new Builder().name("instant-first-rejoin").description("Try immediately after each new disconnect; failed joins use bounded backoff.").defaultValue(true).build());
      this.delay = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("delay-seconds")
            .description("Wait before the first rejoin; doubles each failed try.")
            .defaultValue(0)
            .range(0, 600)
            .sliderRange(0, 120)
            .build()
      );
      this.maxDelay = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-delay-seconds")
            .description("Longest wait between tries.")
            .defaultValue(60)
            .range(30, 3600)
            .sliderRange(60, 1800)
            .build()
      );
      this.kickLoopLimit = sg.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("kick-loop-limit").description("Apply a cooldown after this many disconnects in two minutes. 0 disables.")
         .defaultValue(4).range(0,30).sliderRange(0,10).build());
      this.kickCooldown = sg.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
         .name("kick-loop-cooldown-seconds").description("Wait after a repeated disconnect loop before trying again.")
         .defaultValue(60).range(5,600).sliderRange(5,120).build());
      this.maxAttempts = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-attempts")
            .description("Give up after this many tries in a row. 0 = never.")
            .defaultValue(0)
            .range(0, 100)
            .sliderRange(0, 30)
            .build()
      );
   }

   void setArmed(boolean armed) {
      if (armed != this.armed) {
         this.armed = armed;
      AutoReconnect meteor = Modules.get().get(AutoReconnect.class);
      if (meteor == null) return;
         if (armed) {
            this.meteorWasOn = meteor.isActive();
            if (this.meteorWasOn) {
               meteor.toggle();
            }
         } else if (this.meteorWasOn && !meteor.isActive()) {
            meteor.toggle();
            this.meteorWasOn = false;
         }
      }
   }

   boolean consumeRejoined() {
      boolean r = this.rejoined;
      this.rejoined = false;
      return r;
   }

   boolean consumeSuspicious() {
      boolean s = this.suspicious;
      this.suspicious = false;
      return s;
   }

   String lastKickReason() {
      return this.reason;
   }

   int attempts() {
      return this.attempts;
   }

   String status() {
      if (!this.armed || !this.enabled.get()) return "Reconnect stopped" + (this.reason.isBlank() ? "" : ": " + this.reason);
      return "Reconnect attempt " + this.attempts + " in " + this.waitSeconds() + "s";
   }

   int waitSeconds() { return Math.max(0, (this.countdown + 19) / 20); }

   void reset() {
      this.onScreen = false;
      this.attempts = 0;
      this.awaitingRejoin = false;
      this.rejoined = false;
      this.suspicious = false;
      this.countdown = 0;
      this.connectedTicks = 0;
      this.reason = "";
      this.disconnects.clear();
   }

   void tick() {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1687 != null) {
         this.onScreen = false;
         this.connectedTicks++;
         if (this.awaitingRejoin && this.connectedTicks > 80) {
            this.awaitingRejoin = false;
            this.attempts = 0;
            this.rejoined = true;
         }

         if (this.connectedTicks > 2400) {
            this.attempts = 0;
         }
      } else {
         this.connectedTicks = 0;
         if (!(MeteorClient.mc.field_1755 instanceof class_419 screen)) {
            this.onScreen = false;
         } else if (this.armed && this.enabled.get()) {
            if (!this.onScreen) {
               this.onScreen = true;
               this.reason = readReason(screen);
               String lower = this.reason.toLowerCase(Locale.ROOT);

               for (String b : BAN) {
                  if (lower.contains(b)) {
                     ChatUtils.warningPrefix("Farm Builder", "Looks like a ban, not reconnecting: %s", this.reason);
                     this.setArmed(false);
                     return;
                  }
               }

               boolean sus = false;

               for (String s : SUSPICIOUS) {
                  if (lower.contains(s)) {
                     sus = true;
                  }
               }

               this.suspicious |= sus;
               this.attempts++;
               if (this.maxAttempts.get() > 0 && this.attempts > this.maxAttempts.get()) {
                  this.setArmed(false);
                  return;
               }

               this.countdown = retryTicks(this.instant.get() ? 0 : this.delay.get(), this.maxDelay.get(), this.attempts);
               long now = System.currentTimeMillis();
               while (!this.disconnects.isEmpty() && now - this.disconnects.peekFirst() > 120000L) this.disconnects.removeFirst();
               this.disconnects.addLast(now);
               int cooldown = loopCooldownTicks(this.disconnects.size(), this.kickLoopLimit.get(), this.kickCooldown.get());
               if (cooldown > 0) {
                  this.countdown = Math.max(this.countdown, cooldown);
                  ChatUtils.warningPrefix("Farm Builder", "Repeated disconnects; saving the job and waiting %ds before another join.", this.countdown / 20);
               }

            }

            if (--this.countdown <= 0) {
               AutoReconnect meteor = Modules.get().get(AutoReconnect.class);
               Pair<class_639, class_642> last = meteor == null ? null : meteor.lastServerConnection;
               if (last == null) {
                  this.setArmed(false);
               } else {
                  this.onScreen = false;
                  this.awaitingRejoin = true;
                  class_412.method_36877(new class_442(), MeteorClient.mc, (class_639)last.left(), (class_642)last.right(), false, null);
               }
            }
         }
      }
   }

   static int loopCooldownTicks(int recent, int limit, int seconds) {
      return limit > 0 && recent >= limit ? Math.max(0,seconds) * 20 : 0;
   }

   static int retryTicks(int delay, int maximum, int attempt) {
      if (attempt <= 1) return Math.max(0, delay) * 20;
      double seconds = Math.max(1, delay) * Math.pow(2.0, Math.min(30, attempt - 2));
      return (int) (Math.min(Math.max(1, maximum), seconds) * 20);
   }

   private static String readReason(class_419 screen) {
      StringBuilder sb = new StringBuilder(screen.method_25440().getString());
      try {
         for (java.lang.reflect.Field field : class_419.class.getDeclaredFields()) {
            if (field.getType() == net.minecraft.class_9812.class) {
               field.setAccessible(true);
               net.minecraft.class_9812 info = (net.minecraft.class_9812) field.get(screen);
               if (info != null) sb.append(' ').append(info.comp_2853().getString());
            }
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         MeteorClient.LOG.debug("[Farm Builder] Could not read disconnect details", e);
      }

      for (class_364 e : screen.method_25396()) {
         if (e instanceof class_339 w) {
            sb.append(' ').append(w.method_25369().getString());
         }
      }

      return sb.toString();
   }
}
