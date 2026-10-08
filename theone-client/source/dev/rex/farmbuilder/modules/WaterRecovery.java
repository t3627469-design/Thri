package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.path.IPathExecutor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2350;
import net.minecraft.class_7923;

final class WaterRecovery {
   enum Result { RUNNING, CLEARED, FAILED }

   private int stationarySince = -1;
   private double anchorX;
   private double anchorZ;
   private class_2338 target;
   private class_2680 original;
   private int started;
   private int unreachableSince = -1;
   private final Map<class_2338, Integer> failed = new LinkedHashMap<>();
   private java.util.function.Predicate<class_2338> noBreak = pos -> false;

   void setNoBreak(java.util.function.Predicate<class_2338> noBreak) { this.noBreak = noBreak; }

   boolean stalled(int tick, double x, double z, boolean inWater, boolean trying) {
      if (!inWater || !trying || !Double.isFinite(x) || !Double.isFinite(z)) {
         this.stationarySince = -1;
         return false;
      }
      double dx = x - this.anchorX;
      double dz = z - this.anchorZ;
      if (this.stationarySince < 0 || dx * dx + dz * dz >= 0.16) {
         this.stationarySince = tick;
         this.anchorX = x;
         this.anchorZ = z;
      }
      return tick - this.stationarySince >= 30;
   }

   static List<class_2338> candidates(class_2338 feet, class_2338 destination, boolean upward) {
      List<class_2338> result = new ArrayList<>();
      result.add(feet.method_10084());
      result.add(feet.method_10086(2));
      if (!upward && destination != null) {
         int dx = Integer.compare(destination.method_10263(), feet.method_10263());
         int dz = Integer.compare(destination.method_10260(), feet.method_10260());
         class_2338 ahead = feet.method_10069(dx, 0, dz);
         if (dx != 0 || dz != 0) {
            result.add(ahead.method_10084());
            result.add(ahead);
            result.add(ahead.method_10086(2));
         }
      }
      result.add(feet.method_10086(2));
      result.add(feet.method_10084());
      return result.stream().distinct().toList();
   }

   boolean active() { return this.target != null; }

   boolean begin(IBaritone b, int tick, boolean upward) {
      if (MeteorClient.mc.field_1724 == null || MeteorClient.mc.field_1687 == null) return false;
      class_2338 destination = null;
      IPathExecutor path = b == null ? null : b.getPathingBehavior().getCurrent();
      if (path != null && path.getPosition() >= 0 && path.getPosition() < path.getPath().movements().size())
         destination = path.getPath().movements().get(path.getPosition()).getDest();
      if (destination == null && !upward) {
         double yaw = Math.toRadians(MeteorClient.mc.field_1724.method_36454());
         destination = MeteorClient.mc.field_1724.method_24515().method_10069((int) Math.round(-Math.sin(yaw)), 0, (int) Math.round(Math.cos(yaw)));
      }
      for (class_2338 pos : candidates(MeteorClient.mc.field_1724.method_24515(), destination, upward)) {
         if (this.noBreak.test(pos) || this.coolingDown(pos, tick) || !canBreak(pos) || Look.breakAim(pos) == null) continue;
         this.target = pos.method_10062();
         this.original = MeteorClient.mc.field_1687.method_8320(pos);
         this.started = tick;
         this.unreachableSince = -1;
         if (b != null) b.getInputOverrideHandler().clearAllKeys();
         Look.releaseMovementKeys();
         Look.stopMining();
         return true;
      }
      return false;
   }

   static boolean handEscape(String id) {
      return java.util.Set.of("stone", "deepslate", "cobblestone", "cobbled_deepslate", "andesite", "diorite", "granite", "netherrack", "tuff", "calcite", "sandstone").contains(id);
   }

   static boolean canBreak(class_2338 pos) {
      var world = MeteorClient.mc.field_1687;
      if (world == null || MeteorClient.mc.field_1724 == null
         || !world.method_8393(pos.method_10263() >> 4, pos.method_10260() >> 4)) return false;
      var settings = BaritoneAPI.getSettings();
      class_2680 state = world.method_8320(pos);
      if (!settings.allowBreak.value || settings.blocksToDisallowBreaking.value.contains(state.method_26204())
         || settings.blocksToAvoidBreaking.value.contains(state.method_26204()) || world.method_8321(pos) != null
         || state.method_26204() instanceof net.minecraft.class_2323 || state.method_26204() instanceof net.minecraft.class_2349
         || state.method_26204() instanceof net.minecraft.class_2533
         || state.method_26220(world, pos).method_1110() || state.method_26214(world, pos) < 0) return false;
      if (state.method_29291()) {
         boolean suitable = false;
         for (int i = 0; i < 36; i++) {
            var stack = MeteorClient.mc.field_1724.method_31548().method_5438(i);
            if (FarmLogic.usableGear(stack) && stack.method_7951(state)) { suitable = true; break; }
         }
         if (!suitable && !handEscape(class_7923.field_41175.method_10221(state.method_26204()).method_12832())) return false;
      }
      for (class_2350 direction : class_2350.values()) {
         class_2338 neighbor = pos.method_10093(direction);
         if (!world.method_8393(neighbor.method_10263() >> 4, neighbor.method_10260() >> 4)) return false;
         var fluid = world.method_8316(neighbor);
         if (!fluid.method_15769() && class_7923.field_41173.method_10221(fluid.method_15772()).method_12832().contains("lava")) return false;
      }
      return true;
   }

   Result clear(int tick, boolean swimUp) {
      if (this.target == null) return Result.CLEARED;
      if (MeteorClient.mc.field_1724 == null || MeteorClient.mc.field_1687 == null) return this.fail(tick);
      var world = MeteorClient.mc.field_1687;
      class_2680 state = world.method_8320(this.target);
      if (!MeteorClient.mc.field_1724.method_5799() || state.method_26220(world, this.target).method_1110()) {
         if (state.method_26220(world, this.target).method_1110()) this.rememberFailure(this.target, tick);
         this.release();
         this.stationarySince = tick;
         return Result.CLEARED;
      }
      if (this.noBreak.test(this.target) || state.method_26204() != this.original.method_26204() || !canBreak(this.target) || tick - this.started >= 600)
         return this.fail(tick);
      class_243 aim = Look.breakAim(this.target);
      if (aim == null) {
         if (this.unreachableSince < 0) this.unreachableSince = tick;
         if (tick - this.unreachableSince >= 20) return this.fail(tick);
      } else {
         this.unreachableSince = -1;
         Look.lookAt(aim, 18.0F);
         if (Look.crosshairOn(this.target)) Look.attackCrosshairBlock();
      }
      Look.setKey(MeteorClient.mc.field_1690.field_1903, swimUp);
      return Result.RUNNING;
   }

   boolean coolingDown(class_2338 pos, int tick) {
      this.failed.entrySet().removeIf(entry -> tick - entry.getValue() >= 200);
      return this.failed.containsKey(pos);
   }

   void rememberFailure(class_2338 pos, int tick) {
      this.failed.put(pos.method_10062(), tick);
      while (this.failed.size() > 32) this.failed.remove(this.failed.keySet().iterator().next());
   }

   private Result fail(int tick) {
      if (this.target != null) this.rememberFailure(this.target, tick);
      this.release();
      this.stationarySince = -1;
      return Result.FAILED;
   }

   void abort(int tick) { this.fail(tick); }

   static class_2338 airGap() {
      var world = MeteorClient.mc.field_1687;
      var player = MeteorClient.mc.field_1724;
      if (world == null || player == null) return null;
      class_2338 feet = player.method_24515();
      class_243 eye = player.method_33571();
      class_2338 roof = feet.method_10086(2);
      if (world.method_8320(roof).method_26220(world, roof).method_1110()) return null;
      for (int distance = 1; distance <= 3; distance++) {
         for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            class_2338 pos = feet.method_10069(direction[0] * distance, 0, direction[1] * distance);
            if (!world.method_8393(pos.method_10263() >> 4, pos.method_10260() >> 4)
               || !world.method_8320(pos).method_26220(world, pos).method_1110()
               || !world.method_8320(pos.method_10084()).method_26220(world, pos.method_10084()).method_1110()
               || !world.method_8320(pos.method_10086(2)).method_26220(world, pos.method_10086(2)).method_1110()
               || !world.method_8316(pos.method_10086(2)).method_15769()) continue;
            boolean safe = true;
            for (int step = 1; step <= distance; step++) {
               class_2338 along = feet.method_10069(direction[0] * step, 0, direction[1] * step);
               var fluid = world.method_8316(along);
               if (!world.method_8320(along).method_26220(world, along).method_1110()
                  || fluid.method_15769() || !class_7923.field_41173.method_10221(fluid.method_15772()).method_12832().contains("water")) {
                  safe = false;
                  break;
               }
            }
            if (!safe) continue;
            class_243 point = new class_243(pos.method_10263() + 0.5, eye.field_1351, pos.method_10260() + 0.5);
            var hit = world.method_17742(new net.minecraft.class_3959(eye, point,
               net.minecraft.class_3959.class_3960.field_17559, net.minecraft.class_3959.class_242.field_1348, player));
            if (hit.method_17783() == net.minecraft.class_239.class_240.field_1333) return pos;
         }
      }
      return null;
   }

   void release() {
      if (this.target != null) {
         Look.stopMining();
         Look.releaseMovementKeys();
      }
      this.target = null;
      this.original = null;
      this.unreachableSince = -1;
   }

   void reset() {
      this.release();
      this.stationarySince = -1;
      this.failed.clear();
   }
}
