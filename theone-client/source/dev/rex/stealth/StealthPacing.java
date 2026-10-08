package dev.rex.stealth;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import java.util.Random;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

/**
 * Baritone pacing and sprint pauses, living inside the Farm Builder module's own settings
 * (the separate baritone-stealth module is gone; breaks are Farm Builder's own).
 */
public final class StealthPacing {
   private final Setting<Boolean> pace;
   private final Setting<Integer> lookMin;
   private final Setting<Integer> lookMax;
   private final Setting<Integer> clickMin;
   private final Setting<Integer> clickMax;
   private final Setting<Integer> rerollMin;
   private final Setting<Integer> rerollMax;
   private final Setting<Boolean> breathers;
   private final Setting<Integer> breatherEveryMin;
   private final Setting<Integer> breatherEveryMax;
   private final Setting<Integer> breatherLenMin;
   private final Setting<Integer> breatherLenMax;

   private final Random rng = new Random();
   private int ticks;
   private int nextReroll;
   private int nextBreather;
   private int breatherEnd = -1;
   private Integer originalLookTicks;
   private Integer originalClickSpeed;
   private Boolean originalSmoothLook;
   private Integer writtenLookTicks;
   private Integer writtenClickSpeed;
   private Boolean sprintBeforeBreather;

   public StealthPacing(Settings settings) {
      SettingGroup sgPace = settings.createGroup("Pumpkin Pace");
      SettingGroup sgSprint = settings.createGroup("Ghost Sprint");
      this.pace = sgPace.add(new BoolSetting.Builder()
         .name("vary-pacing")
         .description("Re-rolls Baritone's turn smoothing and block-place delay every so often, so neither stays a fixed number.")
         .defaultValue(true)
         .build());
      this.lookMin = sgPace.add(new IntSetting.Builder()
         .name("turn-ticks-min").description("Fewest ticks Baritone spends on one turn.")
         .defaultValue(5).range(1, 20).sliderRange(1, 20).build());
      this.lookMax = sgPace.add(new IntSetting.Builder()
         .name("turn-ticks-max").description("Most ticks Baritone spends on one turn.")
         .defaultValue(10).range(1, 20).sliderRange(1, 20).build());
      this.clickMin = sgPace.add(new IntSetting.Builder()
         .name("place-delay-min").description("Fewest ticks between block placements.")
         .defaultValue(4).range(1, 20).sliderRange(1, 20).build());
      this.clickMax = sgPace.add(new IntSetting.Builder()
         .name("place-delay-max").description("Most ticks between block placements.")
         .defaultValue(7).range(1, 20).sliderRange(1, 20).build());
      this.rerollMin = sgPace.add(new IntSetting.Builder()
         .name("reroll-seconds-min").description("Shortest time before picking new pacing values.")
         .defaultValue(20).range(5, 600).sliderRange(5, 180).build());
      this.rerollMax = sgPace.add(new IntSetting.Builder()
         .name("reroll-seconds-max").description("Longest time before picking new pacing values.")
         .defaultValue(70).range(5, 600).sliderRange(5, 180).build());
      this.breathers = sgSprint.add(new BoolSetting.Builder()
         .name("sprint-breathers")
         .description("Now and then stops Baritone from sprinting for a few seconds while it walks.")
         .defaultValue(true)
         .build());
      this.breatherEveryMin = sgSprint.add(new IntSetting.Builder()
         .name("every-seconds-min").description("Shortest walking time between breathers.")
         .defaultValue(25).range(5, 600).sliderRange(5, 180).build());
      this.breatherEveryMax = sgSprint.add(new IntSetting.Builder()
         .name("every-seconds-max").description("Longest walking time between breathers.")
         .defaultValue(90).range(5, 600).sliderRange(5, 180).build());
      this.breatherLenMin = sgSprint.add(new IntSetting.Builder()
         .name("length-seconds-min").description("Shortest breather.")
         .defaultValue(1).range(1, 30).sliderRange(1, 15).build());
      this.breatherLenMax = sgSprint.add(new IntSetting.Builder()
         .name("length-seconds-max").description("Longest breather.")
         .defaultValue(4).range(1, 30).sliderRange(1, 15).build());
   }

   public void onActivate() {
      this.ticks = 0;
      this.breatherEnd = -1;
      this.sprintBeforeBreather = null;
      try {
         baritone.api.Settings s = BaritoneAPI.getSettings();
         this.originalLookTicks = s.smoothLookTicks.value;
         this.originalClickSpeed = s.rightClickSpeed.value;
         this.originalSmoothLook = s.smoothLook.value;
      } catch (Throwable t) {
         this.originalLookTicks = null;
         this.originalClickSpeed = null;
         this.originalSmoothLook = null;
      }
      this.nextReroll = 0;
      this.nextBreather = this.secondsFromNow(this.breatherEveryMin, this.breatherEveryMax);
   }

   public void onDeactivate() {
      this.endBreather();
      try {
         baritone.api.Settings s = BaritoneAPI.getSettings();
         if (this.writtenLookTicks != null && this.writtenLookTicks.equals(s.smoothLookTicks.value) && this.originalLookTicks != null)
            s.smoothLookTicks.value = this.originalLookTicks;
         if (this.writtenClickSpeed != null && this.writtenClickSpeed.equals(s.rightClickSpeed.value) && this.originalClickSpeed != null)
            s.rightClickSpeed.value = this.originalClickSpeed;
         if (this.originalSmoothLook != null && this.writtenLookTicks != null && Boolean.TRUE.equals(s.smoothLook.value))
            s.smoothLook.value = this.originalSmoothLook;
      } catch (Throwable ignored) {
      }
      this.writtenLookTicks = null;
      this.writtenClickSpeed = null;
   }

   /** Called every client tick while Farm Builder is on. */
   public void tick() {
      IBaritone b;
      try {
         b = BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable t) {
         return;
      }
      if (b == null) return;
      this.ticks++;
      baritone.api.Settings s = BaritoneAPI.getSettings();
      if (this.pace.get() && this.ticks >= this.nextReroll) this.reroll(s);

      boolean pathing = b.getPathingBehavior().isPathing();
      if (this.breatherEnd >= 0) {
         if (this.ticks >= this.breatherEnd || !this.breathers.get() || !pathing) this.endBreather();
      } else if (this.breathers.get() && pathing && this.ticks >= this.nextBreather) {
         this.sprintBeforeBreather = s.allowSprint.value;
         s.allowSprint.value = false;
         this.breatherEnd = this.ticks + 20 * this.between(this.breatherLenMin, this.breatherLenMax);
      }
   }

   private void reroll(baritone.api.Settings s) {
      int look = this.between(this.lookMin, this.lookMax);
      int click = this.between(this.clickMin, this.clickMax);
      s.smoothLook.value = true;
      s.smoothLookTicks.value = look;
      s.rightClickSpeed.value = click;
      this.writtenLookTicks = look;
      this.writtenClickSpeed = click;
      this.nextReroll = this.secondsFromNow(this.rerollMin, this.rerollMax);
   }

   private void endBreather() {
      if (this.breatherEnd < 0) return;
      this.breatherEnd = -1;
      try {
         baritone.api.Settings s = BaritoneAPI.getSettings();
         if (this.sprintBeforeBreather != null && Boolean.FALSE.equals(s.allowSprint.value)) s.allowSprint.value = this.sprintBeforeBreather;
      } catch (Throwable ignored) {
      }
      this.sprintBeforeBreather = null;
      this.nextBreather = this.secondsFromNow(this.breatherEveryMin, this.breatherEveryMax);
   }

   private int between(Setting<Integer> a, Setting<Integer> b) {
      int lo = Math.min(a.get(), b.get());
      int hi = Math.max(a.get(), b.get());
      return lo + this.rng.nextInt(hi - lo + 1);
   }

   private int secondsFromNow(Setting<Integer> a, Setting<Integer> b) {
      return this.ticks + 20 * this.between(a, b);
   }
}
