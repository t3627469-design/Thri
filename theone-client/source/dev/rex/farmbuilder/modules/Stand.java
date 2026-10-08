package dev.rex.farmbuilder.modules;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

/**
 * Finds a place to stand from which a block that is out of reach can be placed, without flying. Higher layers are
 * reached by standing on the layer below once it is built (or on any other solid block around), so the walk goal is
 * a real standing cell instead of the block itself.
 */
final class Stand {
   private static final int RADIUS = 4;
   private static final double EYE = 1.62;

   private Stand() {
   }

   /**
    * The best cell to stand in (feet position) so that {@code goal} is within {@code reach} of the eyes, or null.
    * Cheapest to get to wins: close to the player, and not much higher or lower than the player now.
    */
   static class_2338 find(class_2338 goal, double reach, Planner planner) {
      var mc = MeteorClient.mc;
      var world = mc.field_1687;
      double px = mc.field_1724.method_23317();
      double py = mc.field_1724.method_23318();
      double pz = mc.field_1724.method_23321();
      double max = Math.max(2.0, reach - 0.5);
      double max2 = max * max;
      class_2338 best = null;
      double bestCost = Double.MAX_VALUE;
      int gx = goal.method_10263();
      int gy = goal.method_10264();
      int gz = goal.method_10260();
      for (int x = gx - RADIUS; x <= gx + RADIUS; x++) {
         for (int z = gz - RADIUS; z <= gz + RADIUS; z++) {
            if (!world.method_8393(x >> 4, z >> 4)) continue;
            for (int y = gy - 4; y <= gy; y++) {
               double ex = x + 0.5 - (gx + 0.5);
               double ey = y + EYE - (gy + 0.5);
               double ez = z + 0.5 - (gz + 0.5);
               if (ex * ex + ey * ey + ez * ez > max2) continue;
               // keep out of the block's own column: it would be inside the player's box
               if (x == gx && z == gz && (gy == y || gy == y + 1)) continue;
               class_2338 feet = new class_2338(x, y, z);
               if (!standable(feet)) continue;
               double dx = x + 0.5 - px;
               double dz = z + 0.5 - pz;
               double cost = Math.sqrt(dx * dx + dz * dz) + 3.0 * Math.abs(y - py);
               // a cell the build still has to fill can be stood in (the player steps off before it is placed), but cells that stay free are better
               if (planner.pending(feet)) cost += 3.0;
               if (planner.pending(feet.method_10084())) cost += 3.0;
               if (cost < bestCost) {
                  bestCost = cost;
                  best = feet;
               }
            }
         }
      }
      return best;
   }

   /** True while {@code spot} can still be stood in (nothing was placed in it, the floor is still there). */
   static boolean stillGood(class_2338 spot) {
      return spot != null && MeteorClient.mc.field_1687.method_8393(spot.method_10263() >> 4, spot.method_10260() >> 4) && standable(spot);
   }

   /** Solid floor, two free cells above it, no liquid. */
   static boolean standable(class_2338 feet) {
      var world = MeteorClient.mc.field_1687;
      class_2338 head = feet.method_10084();
      class_2338 floor = feet.method_10074();
      class_2680 f = world.method_8320(feet);
      class_2680 h = world.method_8320(head);
      class_2680 g = world.method_8320(floor);
      if (!free(f, feet) || !free(h, head)) return false;
      return !g.method_26215() && g.method_26227().method_15769() && !g.method_26220(world, floor).method_1110()
         && !BlockUtils.isClickable(g.method_26204());
   }

   private static boolean free(class_2680 s, class_2338 p) {
      return s.method_26215() || (s.method_26227().method_15769() && s.method_26220(MeteorClient.mc.field_1687, p).method_1110());
   }
}
