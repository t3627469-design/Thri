package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

/**
 * A pillar of spare blocks to get high enough to reach a layer when there is nothing to stand on: jump, place under the
 * feet, repeat. The pillar goes up beside the build (never inside it, so it can't take a spot the build needs) and is
 * broken from the top down once the layer is done. Only blocks this class placed are ever broken: when the block at the
 * top is something else (a hopper, a chest, a block the build put there) it is left alone and the descent goes on.
 */
final class Pillar {
   enum Mode { OFF, WALK, UP, HOLD, DOWN }

   private static final String[] SPARE = {"cobblestone", "dirt", "netherrack", "cobbled_deepslate", "stone", "andesite", "diorite",
      "granite", "tuff", "deepslate", "sandstone", "oak_planks", "spruce_planks"};
   private static final int MAX_HEIGHT = 40;

   private Mode mode = Mode.OFF;
   private final ArrayDeque<class_2338> placed = new ArrayDeque<>();
   private class_2338 base;
   private class_1792 item;
   private int layer = Integer.MIN_VALUE;
   private int ticks;
   private int lastPlaceTick = -100;
   private int misses;
   private boolean failed;

   Mode mode() {
      return this.mode;
   }

   /** World Y of the layer this pillar was built for. */
   int layer() {
      return this.layer;
   }

   boolean hasBlocks() {
      return !this.placed.isEmpty();
   }

   /** True once, right after the pillar gave up (the caller rests the block it was for). */
   boolean takeFailed() {
      boolean f = this.failed;
      this.failed = false;
      return f;
   }

   void reset() {
      this.mode = Mode.OFF;
      this.placed.clear();
      this.base = null;
      this.item = null;
      this.layer = Integer.MIN_VALUE;
      this.ticks = 0;
      this.misses = 0;
      this.failed = false;
      Look.stopMining();
   }

   // ---------------------------------------------------------------- planning

   /** A spare block to build with that the schematic doesn't need, or null. */
   static class_1792 pickItem(Map<class_1792, Integer> have, Set<class_1792> needed) {
      for (String id : SPARE) {
         class_1792 it = class_7923.field_41178.method_63535(class_2960.method_60654("minecraft:" + id));
         if (have.getOrDefault(it, 0) >= 1 && !needed.contains(it)) return it;
      }
      return null;
   }

   /** In creative any of the spare blocks can be conjured. */
   static class_1792 creativeItem() {
      return class_7923.field_41178.method_63535(class_2960.method_60654("minecraft:cobblestone"));
   }

   static long key(int x, int z) {
      return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
   }

   /**
    * The ground cell for a pillar beside the build: a column outside the schematic's footprint, close enough to the
    * block that the top of the pillar reaches it, with solid ground to start on. Nearest to the player wins.
    */
   static class_2338 pickColumn(class_2338 goal, double reach, Set<Long> footprint) {
      var mc = MeteorClient.mc;
      var world = mc.field_1687;
      double px = mc.field_1724.method_23317();
      double py = mc.field_1724.method_23318();
      double pz = mc.field_1724.method_23321();
      double maxH = Math.max(1.0, reach - 1.2);
      class_2338 best = null;
      double bestCost = Double.MAX_VALUE;
      for (int dx = -3; dx <= 3; dx++) {
         for (int dz = -3; dz <= 3; dz++) {
            int x = goal.method_10263() + dx;
            int z = goal.method_10260() + dz;
            if (footprint.contains(key(x, z))) continue;
            if (!world.method_8393(x >> 4, z >> 4)) continue;
            if (Math.sqrt((double) dx * dx + (double) dz * dz) > maxH) continue;
            for (int y = goal.method_10264(); y >= goal.method_10264() - 20; y--) {
               class_2338 feet = new class_2338(x, y, z);
               if (!Stand.standable(feet)) continue;
               double cx = x + 0.5 - px;
               double cz = z + 0.5 - pz;
               double cost = Math.sqrt(cx * cx + cz * cz) + 3.0 * Math.abs(y - py);
               if (cost < bestCost) {
                  bestCost = cost;
                  best = feet;
               }
               break;
            }
         }
      }
      return best;
   }

   /** Starts a pillar: walk to {@code base}, then go up until the block at {@code layerY} can be reached. */
   void start(class_2338 baseCell, class_1792 spare, int layerY) {
      this.reset();
      this.base = baseCell;
      this.item = spare;
      this.layer = layerY;
      this.mode = Mode.WALK;
   }

   // ---------------------------------------------------------------- driving

   /** Walk to the foot of the pillar and centre on it. Returns the status line for the dashboard. */
   String tickWalk(Approach approach) {
      var mc = MeteorClient.mc;
      if (++this.ticks > 900) {
         this.giveUp();
         return "";
      }
      double dx = this.base.method_10263() + 0.5 - mc.field_1724.method_23317();
      double dz = this.base.method_10260() + 0.5 - mc.field_1724.method_23321();
      double hd = Math.sqrt(dx * dx + dz * dz);
      boolean there = this.base.equals(mc.field_1724.method_24515()) || hd < 0.8;
      if (!there) return approach.walk(this.base, 0);
      approach.stop();
      if (hd > 0.2) {
         float[] a = Look.anglesTo(class_243.method_24953(this.base));
         Look.turnTowards(a[0], 8.0F, 20.0F);
         Look.setKey(mc.field_1690.field_1894, true);
         return "Lining up for the pillar";
      }
      Look.setKey(mc.field_1690.field_1894, false);
      this.mode = Mode.UP;
      this.ticks = 0;
      return "Building a pillar";
   }

   /** One tick of jumping and placing. */
   void tickUp(class_2338 goal, double reach, BooleanSupplier holdSpare) {
      var mc = MeteorClient.mc;
      var world = mc.field_1687;
      if (++this.ticks > 1500 || this.placed.size() >= MAX_HEIGHT || this.misses > 6) {
         this.giveUp();
         return;
      }
      // blocks that never showed up (server took them back, something stood in the way)
      class_2338 last = this.placed.peekLast();
      if (last != null && this.ticks - this.lastPlaceTick > 8 && world.method_8320(last).method_26215()) {
         this.placed.pollLast();
         this.misses++;
      }
      class_243 eye = mc.field_1724.method_33571();
      double ex = goal.method_10263() + 0.5 - eye.field_1352;
      double ey = goal.method_10264() + 0.5 - eye.field_1351;
      double ez = goal.method_10260() + 0.5 - eye.field_1350;
      if (Math.sqrt(ex * ex + ey * ey + ez * ez) <= reach - 0.6 && mc.field_1724.method_24828()) {
         Look.setKey(mc.field_1690.field_1903, false);
         Look.setKey(mc.field_1690.field_1894, false);
         this.mode = Mode.HOLD;
         return;
      }
      double dx = this.base.method_10263() + 0.5 - mc.field_1724.method_23317();
      double dz = this.base.method_10260() + 0.5 - mc.field_1724.method_23321();
      if (Math.sqrt(dx * dx + dz * dz) > 0.7) {
         // slid off the column
         this.giveUp();
         return;
      }
      if (!this.hasSpare() || !holdSpare.getAsBoolean()) {
         Look.setKey(mc.field_1690.field_1903, false);
         if (!this.hasSpare()) this.giveUp();
         return;
      }
      class_2338 cell = this.base.method_10069(0, this.placed.size(), 0);
      Look.setKey(mc.field_1690.field_1894, false);
      Look.setKey(mc.field_1690.field_1903, true);
      Look.Placement pl = Look.findPlacement(cell);
      if (pl == null) return;
      boolean aimed = Look.lookAt(pl.hit(), 60.0F);
      if (aimed && mc.field_1724.method_23318() >= cell.method_10264() + 1.02 && Look.crosshairOn(pl.against(), pl.side()) && Look.useCrosshairBlock()) {
         this.placed.add(cell);
         this.lastPlaceTick = this.ticks;
      }
   }

   /** True when the pillar can be broken from the top down (stand on it, look down, mine, repeat). */
   void beginDown() {
      Look.setKey(MeteorClient.mc.field_1690.field_1903, false);
      Look.setKey(MeteorClient.mc.field_1690.field_1894, false);
      this.ticks = 0;
      this.mode = this.placed.isEmpty() ? Mode.OFF : Mode.DOWN;
   }

   /** One tick of taking the pillar down; true when it is gone. */
   boolean tickDown(Approach approach) {
      var mc = MeteorClient.mc;
      var world = mc.field_1687;
      // the top block that is still ours; anything else up there (a hopper, a chest, the build's own block) is left and the descent goes on
      class_2338 top = null;
      while (!this.placed.isEmpty()) {
         class_2338 t = this.placed.peekLast();
         class_2680 st = world.method_8320(t);
         if (st.method_26215() || st.method_26204().method_8389() != this.item || BlockUtils.isClickable(st.method_26204())) {
            this.placed.pollLast();
            continue;
         }
         top = t;
         break;
      }
      if (top == null) {
         this.reset();
         return true;
      }
      if (++this.ticks > 900) {
         this.reset();
         return true;
      }
      double dx = top.method_10263() + 0.5 - mc.field_1724.method_23317();
      double dz = top.method_10260() + 0.5 - mc.field_1724.method_23321();
      boolean onTop = Math.sqrt(dx * dx + dz * dz) < 0.9 && mc.field_1724.method_23318() >= top.method_10264() + 0.9 && mc.field_1724.method_23318() <= top.method_10264() + 2.2;
      if (!onTop) {
         Look.stopMining();
         if (mc.field_1724.method_23318() > top.method_10264() + 2.2 && Math.sqrt(dx * dx + dz * dz) < 0.9) return false; // still falling
         approach.walk(top.method_10084(), 0);
         return false;
      }
      approach.stop();
      class_243 aim = Look.breakAim(top);
      Look.lookAt(aim != null ? aim : class_243.method_24953(top), 50.0F);
      if (Look.crosshairOn(top)) Look.attackCrosshairBlock();
      return false;
   }

   private boolean hasSpare() {
      var mc = MeteorClient.mc;
      try {
         if (mc.field_1724.method_31549().field_7477) return true;
      } catch (Throwable ignored) {
      }
      for (int i = 0; i < 36; i++) {
         class_1799 st = mc.field_1724.method_31548().method_5438(i);
         if (!st.method_7960() && st.method_7909() == this.item) return true;
      }
      return false;
   }

   private void giveUp() {
      Look.setKey(MeteorClient.mc.field_1690.field_1903, false);
      Look.setKey(MeteorClient.mc.field_1690.field_1894, false);
      this.failed = true;
      this.beginDown();
   }
}
