package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_243;
import net.minecraft.class_2338;

/**
 * Gets the player to a block that is out of reach. Baritone does the walking; if the distance has not shrunk for
 * five seconds (Baritone paused, no path, refused the goal) the player is walked by hand toward the block instead,
 * so a build never just stands still because it is far away.
 */
final class Approach {
   private static final int STALL_TICKS = 100;
   private static final int MANUAL_MAX_TICKS = 1200;
   private static final int REISSUE_TICKS = 10;
   private static final int REISSUE_MAX_TICKS = 80;

   private static Boolean savedBreak;
   private static Boolean savedPlace;
   private static Boolean savedParkourPlace;
   private static java.util.List<net.minecraft.class_2248> savedAnyway;
   private static boolean locked;

   /**
    * While a builder runs, Baritone must not mine through the blocks that were just placed, or bridge across gaps
    * with the build's materials. Both are on by default, and every walk the builders start goes through Baritone.
    * The previous values are put back by {@link #unlockSettings()}.
    */
   static void lockSettings() {
      try {
         Settings s = BaritoneAPI.getSettings();
         if (!locked) {
            savedBreak = s.allowBreak.value;
            savedPlace = s.allowPlace.value;
            savedParkourPlace = s.allowParkourPlace.value;
            savedAnyway = s.allowBreakAnyway.value;
            locked = true;
         }
         s.allowBreakAnyway.value = new java.util.ArrayList<>();
         s.allowBreak.value = false;
         s.allowPlace.value = false;
         s.allowParkourPlace.value = false;
      } catch (Throwable ignored) {
      }
   }

   static void unlockSettings() {
      if (!locked) return;
      locked = false;
      try {
         Settings s = BaritoneAPI.getSettings();
         if (savedBreak != null) s.allowBreak.value = savedBreak;
         if (savedPlace != null) s.allowPlace.value = savedPlace;
         if (savedParkourPlace != null) s.allowParkourPlace.value = savedParkourPlace;
         if (savedAnyway != null) s.allowBreakAnyway.value = savedAnyway;
      } catch (Throwable ignored) {
      }
   }

   private class_2338 goal;
   private double best = Double.MAX_VALUE;
   private int tick;
   private int lastImprove;
   private int lastIssue = -100;
   private int reissueGap = REISSUE_TICKS;
   private boolean manual;
   private int manualStart;
   private int jumpHold;
   private int sampleTick;
   private double sampleX;
   private double sampleZ;
   private boolean switched;
   private boolean flying;
   private boolean flewThere;

   /** True once, right after the walk fell back to walking by hand. */
   boolean takeSwitched() {
      boolean s = this.switched;
      this.switched = false;
      return s;
   }

   boolean manualActive() {
      return this.manual;
   }

   void reset() {
      this.stop();
      this.goal = null;
      this.best = Double.MAX_VALUE;
      this.lastIssue = -100;
      this.reissueGap = REISSUE_TICKS;
   }

   /** True in creative mode, where the player can fly. */
   static boolean canFly() {
      try {
         return MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1724.method_31549().field_7477;
      } catch (Throwable t) {
         return false;
      }
   }

   /** True right after {@link #fly} found the player within {@code stopDistance} of the target. */
   boolean flewThere() {
      return this.flewThere;
   }

   /**
    * Creative flight toward a block, with the keys a player would press: fly until the eye is within stopDistance of
    * the block's centre, hovering a little above it. No pathfinding, so nothing is recalculated and height is no problem.
    * Returns a short status line for the dashboard.
    */
   String fly(class_2338 target, double stopDistance) {
      this.tick++;
      lockSettings();
      var mc = MeteorClient.mc;
      IBaritone b = baritone();
      // Baritone switches flight off every tick it moves the player, so it must not be running
      if (b != null && b.getCustomGoalProcess().isActive()) b.getPathingBehavior().cancelEverything();
      this.stop();
      mc.field_1724.method_31549().field_7479 = true;
      class_243 eye = mc.field_1724.method_33571();
      double tx = target.method_10263() + 0.5;
      double ty = target.method_10264() + 0.5;
      double tz = target.method_10260() + 0.5;
      double dx = tx - eye.field_1352;
      double dy = ty - eye.field_1351;
      double dz = tz - eye.field_1350;
      double hd = Math.sqrt(dx * dx + dz * dz);
      double d3 = Math.sqrt(hd * hd + dy * dy);
      this.flying = true;
      if (d3 <= stopDistance) {
         this.flewThere = true;
         Look.releaseMovementKeys();
         return "In place";
      }
      this.flewThere = false;
      float[] angles = Look.anglesTo(new class_243(tx, ty, tz));
      Look.turnTowards(angles[0], 8.0F, 25.0F);
      double err = ty + 1.0 - eye.field_1351;
      Look.setKey(mc.field_1690.field_1894, hd > 1.2);
      Look.setKey(mc.field_1690.field_1903, err > 0.5);
      Look.setKey(mc.field_1690.field_1832, err < -1.0);
      return "Flying to the build (" + (int) d3 + "m)";
   }

   /** Releases the keys the manual walk or the flight pressed. Safe to call any time. */
   void stop() {
      if (this.flying) {
         this.flying = false;
         Look.releaseMovementKeys();
      }
      if (this.manual) {
         this.manual = false;
         Look.releaseMovementKeys();
      }
      this.jumpHold = 0;
   }

   /** Straight-line distance from the player to the block. */
   static double distance(class_2338 p) {
      double dx = MeteorClient.mc.field_1724.method_23317() - (p.method_10263() + 0.5);
      double dy = MeteorClient.mc.field_1724.method_23318() - p.method_10264();
      double dz = MeteorClient.mc.field_1724.method_23321() - (p.method_10260() + 0.5);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /** Distance on the ground plane only, ignoring height. */
   static double horizontal(class_2338 p) {
      double dx = MeteorClient.mc.field_1724.method_23317() - (p.method_10263() + 0.5);
      double dz = MeteorClient.mc.field_1724.method_23321() - (p.method_10260() + 0.5);
      return Math.sqrt(dx * dx + dz * dz);
   }

   /** One tick of walking toward {@code target}; returns a short status line for the dashboard. */
   String walk(class_2338 target, int range) {
      this.tick++;
      lockSettings();
      if (!target.equals(this.goal)) {
         this.stop();
         this.goal = target;
         this.best = Double.MAX_VALUE;
         this.lastImprove = this.tick;
         this.lastIssue = -100;
         this.reissueGap = REISSUE_TICKS;
      }
      double d = distance(target);
      if (d < this.best - 0.75) {
         this.best = d;
         this.lastImprove = this.tick;
         this.reissueGap = REISSUE_TICKS;
      }
      IBaritone b = baritone();
      if (!this.manual && this.tick - this.lastImprove > STALL_TICKS) {
         this.manual = true;
         this.manualStart = this.tick;
         this.switched = true;
         this.sampleTick = this.tick;
         this.sampleX = MeteorClient.mc.field_1724.method_23317();
         this.sampleZ = MeteorClient.mc.field_1724.method_23321();
         if (b != null) b.getPathingBehavior().cancelEverything();
      }
      if (this.manual) {
         if (d <= range || this.tick - this.manualStart > MANUAL_MAX_TICKS) {
            this.stop();
            this.best = d;
            this.lastImprove = this.tick;
         } else {
            this.step(target);
            return "Walking by hand to the build (" + (int) d + "m)";
         }
      }
      // Baritone is only told again when it has stopped, and each time it stops without getting us closer the wait doubles,
      // so a goal with no path doesn't make it recalculate every few ticks
      if (b != null && this.tick - this.lastIssue >= this.reissueGap && !b.getCustomGoalProcess().isActive()) {
         if (this.lastIssue > 0) this.reissueGap = Math.min(this.reissueGap * 2, REISSUE_MAX_TICKS);
         this.lastIssue = this.tick;
         b.getCustomGoalProcess().setGoalAndPath(new GoalNear(target, range));
      }
      return "Walking to the build (" + (int) d + "m)";
   }

   private void step(class_2338 target) {
      class_243 center = class_243.method_24953(target);
      float[] angles = Look.anglesTo(center);
      Look.turnTowards(angles[0], 8.0F, 20.0F);
      boolean ground = Look.groundToward(MeteorClient.mc.field_1724.method_36454());
      Look.setKey(MeteorClient.mc.field_1690.field_1894, ground);
      if (this.tick - this.sampleTick >= 10) {
         double moved = Math.hypot(MeteorClient.mc.field_1724.method_23317() - this.sampleX, MeteorClient.mc.field_1724.method_23321() - this.sampleZ);
         if (ground && moved < 0.3) this.jumpHold = 6;
         this.sampleTick = this.tick;
         this.sampleX = MeteorClient.mc.field_1724.method_23317();
         this.sampleZ = MeteorClient.mc.field_1724.method_23321();
      }
      Look.setKey(MeteorClient.mc.field_1690.field_1903, this.jumpHold > 0);
      if (this.jumpHold > 0) this.jumpHold--;
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable t) {
         return null;
      }
   }
}
