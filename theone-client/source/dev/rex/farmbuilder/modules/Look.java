package dev.rex.farmbuilder.modules;

import java.util.Random;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1297;
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_304;
import net.minecraft.class_3532;
import net.minecraft.class_3959;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_418;
import net.minecraft.class_419;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_490;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

final class Look {
   private static final double BLOCK_REACH = 4.0;
   private static final float DONE_TOLERANCE = 1.5F;
   private static final Random NOISE = new Random();
   private static class_2338 miningPos;
   private static class_437 owned;
   private static int clock;
   private static int expectUntil;
   private static int glanceCooldown = 60;
   private static boolean glancing;
   private static float glanceYaw;
   private static float glancePitch;
   private static float glanceSpeed;

   private Look() {
   }

   static float[] anglesTo(class_243 point) {
      class_243 eye = MeteorClient.mc.field_1724.method_33571();
      double dx = point.field_1352 - eye.field_1352;
      double dy = point.field_1351 - eye.field_1351;
      double dz = point.field_1350 - eye.field_1350;
      double horizontal = Math.sqrt(dx * dx + dz * dz);
      float yaw = class_3532.method_15393((float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
      float pitch = class_3532.method_15363((float)(-Math.toDegrees(Math.atan2(dy, horizontal))), -90.0F, 90.0F);
      return new float[]{yaw, pitch};
   }

   static boolean turnTowards(float yaw, float pitch, float maxStep) {
      if (MeteorClient.mc.field_1724 == null) {
         return false;
      } else {
         pitch = class_3532.method_15363(pitch, -90.0F, 90.0F);
         float curYaw = MeteorClient.mc.field_1724.method_36454();
         float curPitch = MeteorClient.mc.field_1724.method_36455();
         float dYaw = class_3532.method_15393(yaw - curYaw);
         float dPitch = pitch - curPitch;
         if (Math.abs(dYaw) <= 1.5F && Math.abs(dPitch) <= 1.5F) {
            return true;
         } else {
            float gcd = mouseGcd();
            float yawStep = easedStep(dYaw, Math.max(maxStep, 0.1F), gcd);
            float pitchStep = easedStep(dPitch, Math.max(maxStep * 0.7F, 0.1F), gcd);
            MeteorClient.mc.field_1724.method_36456(curYaw + yawStep);
            MeteorClient.mc.field_1724.method_36457(class_3532.method_15363(curPitch + pitchStep, -90.0F, 90.0F));
            float leftYaw = class_3532.method_15393(yaw - MeteorClient.mc.field_1724.method_36454());
            float leftPitch = pitch - MeteorClient.mc.field_1724.method_36455();
            return Math.abs(leftYaw) <= 1.5F && Math.abs(leftPitch) <= 1.5F;
         }
      }
   }

   static boolean lookAt(class_243 point, float maxStep) {
      if (MeteorClient.mc.field_1724 == null) {
         return false;
      } else {
         float[] a = anglesTo(point);
         return turnTowards(a[0], a[1], maxStep);
      }
   }

   private static float easedStep(float delta, float maxStep, float gcd) {
      float abs = Math.abs(delta);
      if (abs <= 0.75F) {
         return 0.0F;
      } else {
         float gain = 0.38F + NOISE.nextFloat() * 0.2F;
         float step = Math.min(abs * gain, maxStep);
         step = Math.max(step, Math.min(abs, 0.6F));
         step += (float)NOISE.nextGaussian() * Math.min(0.2F, step * 0.05F);
         step = Math.min(Math.max(step, 0.0F), abs);
         float snapped = Math.round(step / gcd) * gcd;
         if (snapped <= 0.0F) {
            snapped = gcd;
         }

         if (snapped > abs) {
            snapped = abs;
         }

         return Math.copySign(snapped, delta);
      }
   }

   private static float mouseGcd() {
      double sens = 0.5;

      try {
         Double v = MeteorClient.mc.field_1690.method_42495().method_41753();
         if (v != null) {
            sens = v;
         }
      } catch (RuntimeException var4) {
      }

      double f = sens * 0.6 + 0.2;
      return (float)Math.max(f * f * f * 8.0 * 0.15, 0.001);
   }

   static void idleLook(Random r) {
      if (MeteorClient.mc.field_1724 != null) {
         if (glancing) {
            if (turnTowards(glanceYaw, glancePitch, glanceSpeed)) {
               glancing = false;
               glanceCooldown = 40 + r.nextInt(121);
            }
         } else if (--glanceCooldown <= 0) {
            glanceYaw = class_3532.method_15393(MeteorClient.mc.field_1724.method_36454() + (r.nextFloat() * 70.0F - 35.0F));
            glancePitch = class_3532.method_15363(MeteorClient.mc.field_1724.method_36455() + (r.nextFloat() * 40.0F - 20.0F), -35.0F, 45.0F);
            glanceSpeed = 2.0F + r.nextFloat() * 3.0F;
            glancing = true;
         }
      }
   }

   static boolean crosshairOn(class_2338 pos) {
      return MeteorClient.mc.field_1765 instanceof class_3965 hit && hit.method_17783() == class_240.field_1332 && hit.method_17777().equals(pos);
   }

   static boolean crosshairOn(class_2338 pos, class_2350 side) {
      return crosshairOn(pos) && ((class_3965)MeteorClient.mc.field_1765).method_17780() == side;
   }

   static boolean crosshairOn(class_1297 e) {
      return e != null && MeteorClient.mc.field_1765 instanceof class_3966 hit && hit.method_17783() == class_240.field_1331 && hit.method_17782() == e;
   }

   private static class_3965 crosshairBlock() {
      return MeteorClient.mc.field_1765 instanceof class_3965 hit && hit.method_17783() == class_240.field_1332 ? hit : null;
   }

   static boolean isBreaking(baritone.api.IBaritone b) {
      return b != null && b.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.CLICK_LEFT)
         || MeteorClient.mc.field_1761 != null && MeteorClient.mc.field_1761.method_2923();
   }

   static class_243 breakAim(class_2338 pos) {
      if (MeteorClient.mc.field_1724 == null || MeteorClient.mc.field_1687 == null) return null;
      class_243 eye = MeteorClient.mc.field_1724.method_33571();
      class_243 center = class_243.method_24953(pos);
      double reach = Math.min(BLOCK_REACH, baritone.api.BaritoneAPI.getSettings().blockReachDistance.value);
      java.util.List<class_243> probes = new java.util.ArrayList<>();
      probes.add(center);
      for (class_2350 side : class_2350.values())
         probes.add(center.method_1031(side.method_10148() * 0.45, side.method_10164() * 0.45, side.method_10165() * 0.45));
      for (class_243 probe : probes) {
         class_3965 hit = MeteorClient.mc.field_1687.method_17742(
            new class_3959(eye, probe, class_3960.field_17559, class_242.field_1348, MeteorClient.mc.field_1724));
         if (hit.method_17783() == class_240.field_1332 && hit.method_17777().equals(pos)
            && eye.method_1025(hit.method_17784()) <= reach * reach) return probe;
      }
      return null;
   }

   static boolean useCrosshairBlock() {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1761 != null) {
         class_3965 hit = crosshairBlock();
         if (hit == null) {
            return false;
         } else {
            if (MeteorClient.mc.field_1761.method_2923()) {
               if (miningPos != null) {
                  return true;
               }

               MeteorClient.mc.field_1761.method_2925();
            }

            class_1269 result = MeteorClient.mc.field_1761.method_2896(MeteorClient.mc.field_1724, class_1268.field_5808, hit);
            if (result.method_23665()) {
               MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   static boolean attackCrosshairBlock() {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1687 != null && MeteorClient.mc.field_1761 != null) {
         class_3965 hit = crosshairBlock();
         if (hit != null && !MeteorClient.mc.field_1687.method_8320(hit.method_17777()).method_26215()) {
            class_2338 pos = hit.method_17777().method_10062();
            class_2350 side = hit.method_17780();
            class_1799[] hotbar = new class_1799[9];
            for (int i = 0; i < hotbar.length; i++) hotbar[i] = MeteorClient.mc.field_1724.method_31548().method_5438(i);
            int tool = FarmLogic.bestDigTool(hotbar, MeteorClient.mc.field_1687.method_8320(pos), MeteorClient.mc.field_1724.method_31548().method_67532());
            if (tool >= 0) InvUtils.swap(tool, false);
            boolean swing;
            if (miningPos == null) {
               MeteorClient.mc.field_1761.method_2910(pos, side);
               swing = true;
            } else {
               swing = MeteorClient.mc.field_1761.method_2902(pos, side);
            }

            miningPos = pos;
            if (swing) {
               MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
            }

            return true;
         } else {
            if (miningPos != null) {
               stopMining();
            }

            return false;
         }
      } else {
         return false;
      }
   }

   static void stopMining() {
      miningPos = null;
      if (MeteorClient.mc.field_1761 != null) {
         MeteorClient.mc.field_1761.method_2925();
      }
   }

   static Look.Placement findPlacement(class_2338 target) {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1687 != null) {
         class_243 eye = MeteorClient.mc.field_1724.method_33571();
         Look.Placement best = null;
         double bestDist = Double.MAX_VALUE;

         for (class_2350 dir : class_2350.values()) {
            class_2338 against = target.method_10093(dir);
            class_2680 state = MeteorClient.mc.field_1687.method_8320(against);
            if (!state.method_26215()
               && !state.method_45474()
               && state.method_26227().method_15769()
               && !state.method_26220(MeteorClient.mc.field_1687, against).method_1110()
               && !BlockUtils.isClickable(state.method_26204())) {
               class_2350 side = dir.method_10153();
               class_243 normal = new class_243(side.method_10148(), side.method_10164(), side.method_10165());
               class_243 hit = class_243.method_24953(against).method_1019(normal.method_1021(0.5));
               double dist = eye.method_1022(hit);
               if (!(dist > 4.0) && !(dist >= bestDist) && !(eye.method_1020(hit).method_1026(normal) <= 0.0)) {
                  class_243 probe = class_243.method_24953(against).method_1019(normal.method_1021(0.45));
                  class_3965 ray = MeteorClient.mc
                     .field_1687
                     .method_17742(new class_3959(eye, probe, class_3960.field_17559, class_242.field_1348, MeteorClient.mc.field_1724));
                  if (ray != null && ray.method_17783() == class_240.field_1332 && ray.method_17777().equals(against) && ray.method_17780() == side) {
                     best = new Look.Placement(against, side, hit);
                     bestDist = dist;
                  }
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static boolean selectHotbar(Predicate<class_1799> match) {
      if (MeteorClient.mc.field_1724 == null) {
         return false;
      } else {
         int selected = MeteorClient.mc.field_1724.method_31548().method_67532();
         if (match.test(MeteorClient.mc.field_1724.method_31548().method_5438(selected))) {
            return true;
         } else {
            for (int i = 0; i < 9; i++) {
               class_1799 stack = MeteorClient.mc.field_1724.method_31548().method_5438(i);
               if (!stack.method_7960() && match.test(stack)) {
                  return InvUtils.swap(i, false);
               }
            }

            return false;
         }
      }
   }

   static void expectScreen() {
      expectScreen(80);
   }

   static void expectScreen(int ticks) { expectUntil = clock + Math.max(1, ticks); }

   static void tickScreens() {
      clock++;
      class_437 s = MeteorClient.mc.field_1755;
      if (s == null) {
         owned = null;
      } else {
         if (s != owned && s instanceof class_465 && clock <= expectUntil) {
            owned = s;
            expectUntil = 0;
         }
      }
   }

   static boolean playerScreenOpen() {
      return playerScreenOpen(MeteorClient.mc.field_1755);
   }

   static boolean playerScreenOpen(class_437 s) {
      return s != null && s != owned && !(s instanceof class_418) && !(s instanceof class_419);
   }

   static boolean openInventoryIfClosed() {
      if (MeteorClient.mc.field_1724 == null) {
         return false;
      } else {
         if (MeteorClient.mc.field_1755 == null) {
            MeteorClient.mc.method_1507(new class_490(MeteorClient.mc.field_1724));
            owned = MeteorClient.mc.field_1755;
         }

         return MeteorClient.mc.field_1755 instanceof class_490;
      }
   }

   static void closeScreen() {
      if (MeteorClient.mc.field_1755 instanceof class_465 && MeteorClient.mc.field_1724 != null) {
         MeteorClient.mc.field_1724.method_7346();
      } else if (MeteorClient.mc.field_1755 != null) {
         MeteorClient.mc.method_1507(null);
      }
   }

   static int inventorySlotToHandlerSlot(int invIndex) {
      if (invIndex >= 0 && invIndex < 9) {
         return invIndex + 36;
      } else if (invIndex >= 9 && invIndex < 36) {
         return invIndex;
      } else if (invIndex >= 36 && invIndex < 40) {
         return 8 - (invIndex - 36);
      } else {
         return invIndex == 40 ? 45 : -1;
      }
   }

   static void clickSwapToHotbar(int invIndex, int hotbarIndex) {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1761 != null) {
         if (MeteorClient.mc.field_1755 instanceof class_490) {
            int slot = inventorySlotToHandlerSlot(invIndex);
            if (slot >= 0 && hotbarIndex >= 0 && hotbarIndex <= 8) {
               MeteorClient.mc
                  .field_1761
                  .method_2906(MeteorClient.mc.field_1724.field_7498.field_7763, slot, hotbarIndex, class_1713.field_7791, MeteorClient.mc.field_1724);
            }
         }
      }
   }

   static void setKey(class_304 key, boolean down) {
      if (key != null) {
         key.method_23481(down);
      }
   }

   static boolean groundToward(float yaw) {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1687 != null) {
         double rad = Math.toRadians(yaw);
         class_2338 ahead = class_2338.method_49637(
            MeteorClient.mc.field_1724.method_23317() - Math.sin(rad) * 1.2,
            MeteorClient.mc.field_1724.method_23318(),
            MeteorClient.mc.field_1724.method_23321() + Math.cos(rad) * 1.2
         );
         if (MeteorClient.mc.field_1687.method_8316(ahead).method_15769() && MeteorClient.mc.field_1687.method_8316(ahead.method_10074()).method_15769()) {
            for (int dy = 1; dy <= 3; dy++) {
               class_2338 p = ahead.method_10087(dy);
               if (!MeteorClient.mc.field_1687.method_8320(p).method_26220(MeteorClient.mc.field_1687, p).method_1110()) {
                  return true;
               }
            }

            return false;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   static void releaseMovementKeys() {
      if (MeteorClient.mc.field_1690 != null) {
         setKey(MeteorClient.mc.field_1690.field_1894, false);
         setKey(MeteorClient.mc.field_1690.field_1881, false);
         setKey(MeteorClient.mc.field_1690.field_1913, false);
         setKey(MeteorClient.mc.field_1690.field_1849, false);
         setKey(MeteorClient.mc.field_1690.field_1903, false);
         setKey(MeteorClient.mc.field_1690.field_1832, false);
         setKey(MeteorClient.mc.field_1690.field_1867, false);
         setKey(MeteorClient.mc.field_1690.field_1886, false);
         setKey(MeteorClient.mc.field_1690.field_1904, false);
      }
   }

   record Placement(class_2338 against, class_2350 side, class_243 hit) {
   }
}
