package dev.rex.farmbuilder.modules;

import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_10255;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_3532;

final class Boat {
   static final Set<class_1792> BOATS = Set.of(
      class_1802.field_8533,
      class_1802.field_8486,
      class_1802.field_8442,
      class_1802.field_8730,
      class_1802.field_8094,
      class_1802.field_8138,
      class_1802.field_37531,
      class_1802.field_42706,
      class_1802.field_54620,
      class_1802.field_40224
   );
   private Boat.Step step = Boat.Step.NONE;
   private int ticks;
   private int stepStart;
   private int swimTicks;
   private int slowTicks;
   private int cooldownUntil;
   private class_10255 boat;

   boolean isActive() {
      return this.step != Boat.Step.NONE;
   }

   String status() {
      return switch (this.step) {
         case PLACE -> "Putting a boat down";
         case MOUNT -> "Getting in the boat";
         case RIDE -> "Boating";
         case DISMOUNT, PICKUP -> "Getting out, picking the boat up";
         default -> null;
      };
   }

   void reset() {
      if (this.step != Boat.Step.NONE) {
         Look.releaseMovementKeys();
      }

      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1724.method_5765()) {
         Look.setKey(MeteorClient.mc.field_1690.field_1832, true);
      }

      this.step = Boat.Step.NONE;
      this.boat = null;
   }

   boolean tick(class_2338 goal) {
      this.ticks++;
      if (MeteorClient.mc.field_1724 != null && goal != null) {
         double dist = horizontalDist(goal);
         if (this.step == Boat.Step.NONE) {
            this.swimTicks = MeteorClient.mc.field_1724.method_5799() && !MeteorClient.mc.field_1724.method_24828() ? this.swimTicks + 1 : 0;
            if (this.swimTicks < 40 || dist < 40.0 || this.ticks < this.cooldownUntil || this.boatSlot() < 0) {
               return false;
            }

            this.begin(Boat.Step.PLACE);
         }

         if (this.ticks - this.stepStart > 400 && this.step != Boat.Step.RIDE) {
            this.reset();
            this.cooldownUntil = this.ticks + 1200;
            return false;
         } else {
            switch (this.step) {
               case PLACE:
                  int slot = this.boatSlot();
                  if (slot < 0) {
                     this.reset();
                     return false;
                  }

                  if (slot >= 9) {
                     if (Look.openInventoryIfClosed()) {
                        Look.clickSwapToHotbar(slot, 5);
                     }

                     return true;
                  }

                  if (MeteorClient.mc.field_1755 != null) {
                     Look.closeScreen();
                  }

                  Look.selectHotbar(st -> BOATS.contains(st.method_7909()));
                  float[] toGoal = Look.anglesTo(class_243.method_24953(goal));
                  if (!Look.turnTowards(toGoal[0], 55.0F, 15.0F)) {
                     return true;
                  }

                  MeteorClient.mc.field_1761.method_2919(MeteorClient.mc.field_1724, class_1268.field_5808);
                  MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
                  this.begin(Boat.Step.MOUNT);
                  break;
               case MOUNT:
                  if (MeteorClient.mc.field_1724.method_5854() instanceof class_10255 b) {
                     this.boat = b;
                     this.begin(Boat.Step.RIDE);
                     return true;
                  }

                  class_10255 near = this.nearestBoat();
                  if (near == null) {
                     return true;
                  }

                  Look.lookAt(new class_243(near.method_23317(), near.method_23318() + 0.3, near.method_23321()), 15.0F);
                  if (Look.crosshairOn(near) && (this.ticks - this.stepStart) % 5 == 0) {
                     MeteorClient.mc.field_1761.method_2905(MeteorClient.mc.field_1724, near, class_1268.field_5808);
                  }
                  break;
               case RIDE:
                  if (!(MeteorClient.mc.field_1724.method_5854() instanceof class_10255 b)) {
                     this.begin(Boat.Step.PICKUP);
                     return true;
                  }

                  this.boat = b;
                  float[] rideGoal = Look.anglesTo(class_243.method_24953(goal));
                  float diff = class_3532.method_15393(rideGoal[0] - b.method_36454());
                  Look.setKey(MeteorClient.mc.field_1690.field_1913, diff < -8.0F);
                  Look.setKey(MeteorClient.mc.field_1690.field_1849, diff > 8.0F);
                  Look.setKey(MeteorClient.mc.field_1690.field_1894, Math.abs(diff) < 70.0F);
                  Look.turnTowards(rideGoal[0], 10.0F, 6.0F);
                  double speed = b.method_18798().method_37267();
                  this.slowTicks = speed < 0.05 ? this.slowTicks + 1 : 0;
                  if (dist < 8.0 || this.slowTicks > 40 || this.ticks - this.stepStart > 3600) {
                     this.begin(Boat.Step.DISMOUNT);
                  }
                  break;
               case DISMOUNT:
                  Look.setKey(MeteorClient.mc.field_1690.field_1894, false);
                  Look.setKey(MeteorClient.mc.field_1690.field_1913, false);
                  Look.setKey(MeteorClient.mc.field_1690.field_1849, false);
                  Look.setKey(MeteorClient.mc.field_1690.field_1832, true);
                  if (!MeteorClient.mc.field_1724.method_5765() && this.ticks - this.stepStart > 3) {
                     Look.setKey(MeteorClient.mc.field_1690.field_1832, false);
                     this.begin(Boat.Step.PICKUP);
                  }
                  break;
               case PICKUP:
                  if (this.boat == null || !this.boat.method_5805() || MeteorClient.mc.field_1724.method_5739(this.boat) > 4.5) {
                     this.step = Boat.Step.NONE;
                     this.cooldownUntil = this.ticks + 200;
                     return false;
                  }

                  Look.lookAt(new class_243(this.boat.method_23317(), this.boat.method_23318() + 0.3, this.boat.method_23321()), 15.0F);
                  if (Look.crosshairOn(this.boat) && MeteorClient.mc.field_1724.method_7261(0.5F) >= 0.9F) {
                     MeteorClient.mc.field_1761.method_2918(MeteorClient.mc.field_1724, this.boat);
                     MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
                  }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private void begin(Boat.Step s) {
      this.step = s;
      this.stepStart = this.ticks;
      this.slowTicks = 0;
   }

   private int boatSlot() {
      for (int i = 0; i < 36; i++) {
         class_1799 st = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (BOATS.contains(st.method_7909())) {
            return i;
         }
      }

      return -1;
   }

   private class_10255 nearestBoat() {
      class_10255 best = null;
      double bestDist = 4.0;

      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_10255 b && !e.method_5782()) {
            double d = MeteorClient.mc.field_1724.method_5739(e);
            if (d < bestDist) {
               bestDist = d;
               best = b;
            }
         }
      }

      return best;
   }

   private static double horizontalDist(class_2338 p) {
      double dx = p.method_10263() + 0.5 - MeteorClient.mc.field_1724.method_23317();
      double dz = p.method_10260() + 0.5 - MeteorClient.mc.field_1724.method_23321();
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static enum Step {
      NONE,
      PLACE,
      MOUNT,
      RIDE,
      DISMOUNT,
      PICKUP;
   }
}
