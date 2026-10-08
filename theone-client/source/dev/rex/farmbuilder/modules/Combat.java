package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalRunAway;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.class_10730;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1308;
import net.minecraft.class_1309;
import net.minecraft.class_1321;
import net.minecraft.class_1428;
import net.minecraft.class_1429;
import net.minecraft.class_1439;
import net.minecraft.class_1452;
import net.minecraft.class_1463;
import net.minecraft.class_1472;
import net.minecraft.class_1473;
import net.minecraft.class_1547;
import net.minecraft.class_1548;
import net.minecraft.class_1560;
import net.minecraft.class_1569;
import net.minecraft.class_1590;
import net.minecraft.class_1593;
import net.minecraft.class_1604;
import net.minecraft.class_1621;
import net.minecraft.class_1640;
import net.minecraft.class_1646;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_304;
import net.minecraft.class_3989;
import net.minecraft.class_4836;
import net.minecraft.class_5354;

final class Combat {
   private static final List<class_1792> WEAPONS = List.of(
      class_1802.field_22022,
      class_1802.field_8802,
      class_1802.field_8371,
      class_1802.field_8528,
      class_1802.field_8845,
      class_1802.field_8091,
      class_1802.field_22025,
      class_1802.field_8556,
      class_1802.field_8475,
      class_1802.field_8062
   );
   private final Setting<Boolean> enabled;
   private final Setting<Double> reach;
   private final Setting<Double> engageRange;
   private final Setting<Double> aimSpeed;
   private final Setting<Integer> extraDelay;
   private final Setting<Integer> critChance;
   private final Setting<Boolean> hopToMobs;
   private final Setting<Boolean> shield;
   private final Setting<Boolean> totem;
   private final Setting<Integer> retreatHealth;
   private final Setting<Integer> awareRange;
   private final Setting<Boolean> proactive;
   private final Setting<Boolean> flee;
   private final Setting<Integer> fleeCount;
   private final Random random = new Random();
   private class_1309 target;
   private boolean fighting;
   private int ticks;
   private int lastSeen;
   private int nextHit;
   private float aimHeight = 0.6F;
   private int strafeDir;
   private int strafeUntil;
   private int critPhase;
   private int retreatUntil;
   private int invCloseAt = -1;
   private boolean hunting;
   private final Set<class_304> held = new HashSet<>();
   private final List<class_1309> aware = new ArrayList<>();
   private boolean fleeing;
   private boolean fleeEnded;
   private int fleeStart;
   private int fleeSafeSince = -1;
   private int nextFleeGoal;
   private int lastNotice = -10000;
   private int jumpUntil;
   private int critStart;
   private int nextHop;
   private int lastWeaponFetch = -100000;
   private int lastTotemTry = -100000;
   private static final int WEAPON_SLOT = 1;

   Combat(Settings settings) {
      SettingGroup sg = settings.createGroup("Combat");
      this.enabled = sg.add(new Builder().name("attack-mobs").description("Fight hostile mobs that come close.").defaultValue(true).build());
      this.reach = sg.add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("attack-range")
            .description("Hit range. 3 is vanilla-safe.")
            .defaultValue(3.0)
            .range(2.0, 4.5)
            .sliderRange(2.0, 4.0)
            .build()
      );
      this.engageRange = sg.add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("engage-range")
            .description("Walk toward mobs closer than this.")
            .defaultValue(6.0)
            .range(3.0, 16.0)
            .sliderRange(3.0, 10.0)
            .build()
      );
      this.aimSpeed = sg.add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("aim-speed")
            .description("Max camera turn per tick. Lower looks more human.")
            .defaultValue(18.0)
            .range(4.0, 90.0)
            .sliderRange(6.0, 40.0)
            .build()
      );
      this.extraDelay = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("extra-delay")
            .description("Random extra ticks after the cooldown before each hit.")
            .defaultValue(3)
            .range(0, 20)
            .sliderMax(10)
            .build()
      );
      this.critChance = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("crit-chance")
            .description("Percent of hits done as jump-crits (jump, hit on the way down: 1.5x damage).")
            .defaultValue(70)
            .range(0, 100)
            .sliderRange(0, 100)
            .build()
      );
      this.hopToMobs = sg.add(
         new Builder()
            .name("sprint-jump-to-mobs")
            .description("Sprint-jump toward mobs that are a few blocks away, like players do.")
            .defaultValue(true)
            .build()
      );
      this.shield = sg.add(new Builder().name("use-shield").description("Block arrows with a shield in the offhand.").defaultValue(true).build());
      this.totem = sg.add(new Builder().name("auto-totem").description("Move a totem to the offhand when health gets low.").defaultValue(true).build());
      this.retreatHealth = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("retreat-health")
            .description("Run from fights at or below this health (2 = 1 heart).")
            .defaultValue(8)
            .range(0, 19)
            .sliderMax(19)
            .build()
      );
      this.awareRange = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("awareness-range")
            .description("Watch for mobs that have spotted you (line of sight + coming at you / facing you / drawing a bow) out to this range.")
            .defaultValue(16)
            .range(6, 32)
            .sliderRange(8, 24)
            .build()
      );
      this.proactive = sg.add(
         new Builder()
            .name("go-to-them")
            .description("Turn to face mobs that spotted you and walk up to fight them instead of waiting to get hit.")
            .defaultValue(true)
            .build()
      );
      this.flee = sg.add(
         new Builder()
            .name("run-when-losing")
            .description("Run (Baritone run-away pathing) when outnumbered, low, unarmed or next to a fusing creeper. Goes back to work after.")
            .defaultValue(true)
            .build()
      );
      this.fleeCount = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("outnumbered-at")
            .description("Run when this many mobs have spotted you at once. 0 = never for numbers alone.")
            .defaultValue(4)
            .range(0, 12)
            .sliderRange(0, 8)
            .build()
      );
   }

   boolean ownsScreen() {
      return this.invCloseAt > 0;
   }

   boolean threatIncoming() {
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1687 != null && this.enabled.get()) {
         for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
            if (e instanceof class_1308 mob && mob.method_5805() && !this.isFoodAnimal(e) && !(this.weight(e) <= 0.0)) {
               double d = MeteorClient.mc.field_1724.method_5739(e);
               if (!(d > this.awareRange.get().intValue()) && mob.method_6057(MeteorClient.mc.field_1724)) {
                  boolean ranged = e instanceof class_1547 || e instanceof class_1604;
                  if (d <= 5.0
                     || d <= 10.0 && (this.movingToward(mob) || this.facingUs(mob) || mob.method_5968() == MeteorClient.mc.field_1724)
                     || e instanceof class_1548 && d <= 8.0
                     || ranged && mob.method_6115()) {
                     return true;
                  }
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   boolean needsBaritone() {
      return this.fleeing;
   }

   boolean consumeFleeEnded() {
      boolean f = this.fleeEnded;
      this.fleeEnded = false;
      return f;
   }

   int spotted() {
      return this.aware.size();
   }

   String status() {
      if (this.fleeing) {
         return "Running from " + this.aware.size() + " mob" + (this.aware.size() == 1 ? "" : "s");
      } else if (this.ticks < this.retreatUntil) {
         return "Retreating (low health)";
      } else {
         return this.fighting && this.target != null && MeteorClient.mc.field_1724 != null
            ? "Fighting " + this.target.method_5477().getString() + " (" + (int)MeteorClient.mc.field_1724.method_5739(this.target) + "m)"
            : null;
      }
   }

   boolean isFighting() {
      return this.fighting;
   }

   void setHunting(boolean hunting) {
      this.hunting = hunting;
   }

   boolean dangerLow() {
      return MeteorClient.mc.field_1724 != null && this.ticks < this.retreatUntil;
   }

   void reset() {
      this.releaseKeys();
      this.target = null;
      this.fighting = false;
      this.hunting = false;
      this.critPhase = 0;
      this.retreatUntil = 0;
      if (this.fleeing) {
         this.fleeing = false;
         IBaritone b = baritone();
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }
      }

      this.fleeEnded = false;
      this.aware.clear();
      if (this.invCloseAt > 0) {
         Look.closeScreen();
      }

      this.invCloseAt = -1;
   }

   class_1297 huntTarget() {
      if (this.hunting && MeteorClient.mc.field_1724 != null) {
         class_1297 best = null;
         double bestDist = 48.0;

         for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
            if (this.isFoodAnimal(e)) {
               double d = MeteorClient.mc.field_1724.method_5739(e);
               if (d < bestDist) {
                  bestDist = d;
                  best = e;
               }
            }
         }

         return best;
      } else {
         return null;
      }
   }

   boolean tick() {
      this.ticks++;
      if (MeteorClient.mc.field_1724 == null || MeteorClient.mc.field_1687 == null) {
         return false;
      } else if (this.invCloseAt > 0) {
         if (this.ticks >= this.invCloseAt) {
            Look.closeScreen();
            this.invCloseAt = -1;
         }

         return true;
      } else if (!this.enabled.get() && !this.hunting) {
         // Turning attack-mobs off mid-escape must also end the escape (flag and Baritone run-away path).
         if (this.fleeing) {
            this.reset();
            return false;
         }
         return this.stop();
      } else {
         if (this.jumpUntil > 0 && this.ticks >= this.jumpUntil) {
            this.press(MeteorClient.mc.field_1690.field_1903, false);
            this.jumpUntil = 0;
         }

         if (this.critPhase == 1 && this.ticks - this.critStart > 15) {
            this.critPhase = 0;
         }

         this.scanAware();
         if (this.fleeing) {
            return this.tickFlee();
         } else if (this.shouldFlee() && this.startFlee()) {
            return true;
         } else if (this.ticks < this.retreatUntil) {
            this.retreat();
            return true;
         } else {
            class_1309 t = this.pickTarget();
            if (t == null) {
               if (this.fighting && this.ticks - this.lastSeen > 20 + this.random.nextInt(20)) {
                  this.stop();
               } else {
                  this.releaseKeys();
               }

               return this.fighting;
            } else {
               if (t != this.target) {
                  this.releaseKeys();
                  this.target = t;
                  this.aimHeight = 0.45F + this.random.nextFloat() * 0.3F;
                  this.critPhase = 0;
               }

               this.lastSeen = this.ticks;
               this.fighting = true;
               float health = MeteorClient.mc.field_1724.method_6032();
               if (health <= this.retreatHealth.get().intValue() && this.hostileWithin(8.0) && !(t instanceof class_1548)) {
                  this.retreatUntil = this.ticks + 40 + this.random.nextInt(30);
                  this.retreat();
                  return true;
               } else if (this.manageOffhand(health)) {
                  return true;
               } else if (this.manageWeapon()) {
                  return true;
               } else {
                  double dist = MeteorClient.mc.field_1724.method_5739(t);
                  if (!(t instanceof class_1548 creeper && (creeper.method_7007() > 0 || creeper.method_7000() || dist < 2.2))) {
                     Look.lookAt(this.aimPoint(t), this.aimSpeed.get().floatValue() * (0.65F + this.random.nextFloat() * 0.35F));
                     this.move(t, dist);
                     this.block(t, dist);
                     this.hit(t, dist);
                     return true;
                  } else {
                     this.avoidCreeper(creeper, dist);
                     return true;
                  }
               }
            }
         }
      }
   }

   private class_1309 pickTarget() {
      class_1309 best = null;
      double bestScore = 0.0;
      double maxDist = Math.max(this.engageRange.get(), this.reach.get() + 1.5);

      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1309 living && e != MeteorClient.mc.field_1724 && living.method_5805() && !living.method_29504()) {
            double d = MeteorClient.mc.field_1724.method_5739(e);
            boolean spotted = this.proactive.get() && this.aware.contains(living) && d <= 10.0;
            if (!(d > maxDist) || spotted) {
               double weight = this.weight(e);
               if (!(weight <= 0.0)) {
                  boolean creeperClose = e instanceof class_1548 && d < 5.0;
                  if ((creeperClose || MeteorClient.mc.field_1724.method_6057(e))
                     && (!(Math.abs(e.method_23318() - MeteorClient.mc.field_1724.method_23318()) > 3.5) || e instanceof class_1593)) {
                     double score = weight / Math.max(d, 0.5) * (spotted ? 1.3 : 1.0);
                     if (e instanceof class_1308 mob && mob.method_5968() == MeteorClient.mc.field_1724) {
                        score *= 1.5;
                     }

                     if (score > bestScore) {
                        bestScore = score;
                        best = living;
                     }
                  }
               }
            }
         }
      }

      return best;
   }

   private double weight(class_1297 e) {
      if (!(e instanceof class_1657) && !e.method_16914()) {
         if (e instanceof class_1321 tame && tame.method_6181()) {
            return 0.0;
         } else if (!(e instanceof class_1646) && !(e instanceof class_3989) && !(e instanceof class_1439) && !(e instanceof class_1473)) {
            boolean targetingUs = e instanceof class_1308 mob && mob.method_5968() == MeteorClient.mc.field_1724;
            if (e instanceof class_1560 || e instanceof class_1590 || e instanceof class_4836 || e instanceof class_5354) {
               return targetingUs ? 2.0 : 0.0;
            } else if (e instanceof class_1548 c) {
               return c.method_7007() > 0 ? 10.0 : 5.0;
            } else if (e instanceof class_1547 || e instanceof class_1604 || e instanceof class_1640 || e instanceof class_1593) {
               return 4.0;
            } else if (e instanceof class_1621) {
               return 1.5;
            } else if (e instanceof class_1569) {
               return 3.0;
            } else {
               return this.hunting && this.isFoodAnimal(e) ? 1.0 : 0.0;
            }
         } else {
            return 0.0;
         }
      } else {
         return 0.0;
      }
   }

   private boolean isFoodAnimal(class_1297 e) {
      return e instanceof class_1429 a && !a.method_6109() && !e.method_16914()
         ? e instanceof class_10730 || e instanceof class_1452 || e instanceof class_1428 || e instanceof class_1472 || e instanceof class_1463
         : false;
   }

   private boolean hostileWithin(double r) {
      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1569 && e instanceof class_1309 l && l.method_5805() && MeteorClient.mc.field_1724.method_5739(e) <= r) {
            return true;
         }
      }

      return false;
   }

   private class_243 aimPoint(class_1309 t) {
      return new class_243(t.method_23317(), t.method_23318() + t.method_17682() * this.aimHeight, t.method_23321());
   }

   private void hit(class_1309 t, double dist) {
      if (!(dist > this.reach.get()) && Look.crosshairOn(t) && this.ticks >= this.nextHit) {
         if (!(MeteorClient.mc.field_1724.method_7261(0.5F) < 0.92F + this.random.nextFloat() * 0.08F)) {
            boolean canCrit = this.critChance.get() > 0
               && !(t instanceof class_1548)
               && !MeteorClient.mc.field_1724.method_5799()
               && !MeteorClient.mc.field_1724.method_6101();
            if (canCrit && this.critPhase == 0 && MeteorClient.mc.field_1724.method_24828() && this.random.nextInt(100) < this.critChance.get()) {
               this.tapJump();
               this.critPhase = 1;
               this.critStart = this.ticks;
            } else {
               if (this.critPhase == 1) {
                  if (MeteorClient.mc.field_1724.method_24828() || MeteorClient.mc.field_1724.method_18798().field_1351 >= 0.0) {
                     return;
                  }

                  this.critPhase = 0;
               }

               MeteorClient.mc.field_1761.method_2918(MeteorClient.mc.field_1724, t);
               MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
               this.nextHit = this.ticks + 1 + this.random.nextInt(this.extraDelay.get() + 1);
               if (this.held.contains(MeteorClient.mc.field_1690.field_1894)) {
                  this.press(MeteorClient.mc.field_1690.field_1894, false);
               }
            }
         }
      }
   }

   private void move(class_1309 t, double dist) {
      double approach = this.proactive.get() && this.aware.contains(t) ? Math.max(10.0, this.engageRange.get()) : this.engageRange.get();
      boolean wantForward = dist > 2.6 && dist <= approach && this.safeAhead();
      this.press(MeteorClient.mc.field_1690.field_1894, wantForward);
      boolean closing = wantForward && dist > 3.5 && !MeteorClient.mc.field_1724.method_5799();
      this.press(MeteorClient.mc.field_1690.field_1867, closing);
      if (this.hopToMobs.get() && closing && MeteorClient.mc.field_1724.method_24828() && this.critPhase == 0 && this.ticks >= this.nextHop) {
         this.tapJump();
         this.nextHop = this.ticks + 10 + this.random.nextInt(14);
      }

      boolean ranged = t instanceof class_1547 || t instanceof class_1604 || t instanceof class_1640;
      if (ranged && wantForward) {
         if (this.ticks >= this.strafeUntil) {
            this.strafeDir = this.random.nextInt(3) - 1;
            this.strafeUntil = this.ticks + 10 + this.random.nextInt(12);
         }

         this.press(MeteorClient.mc.field_1690.field_1913, this.strafeDir < 0);
         this.press(MeteorClient.mc.field_1690.field_1849, this.strafeDir > 0);
      } else {
         this.press(MeteorClient.mc.field_1690.field_1913, false);
         this.press(MeteorClient.mc.field_1690.field_1849, false);
      }
   }

   private void block(class_1309 t, double dist) {
      boolean hasShield = MeteorClient.mc.field_1724.method_6079().method_7909() == class_1802.field_8255;
      boolean aimedAt = (t instanceof class_1547 || t instanceof class_1604) && t.method_6115();
      this.press(MeteorClient.mc.field_1690.field_1904, this.shield.get() && hasShield && aimedAt && dist > 4.0);
   }

   private void avoidCreeper(class_1548 creeper, double dist) {
      Look.lookAt(this.aimPoint(creeper), this.aimSpeed.get().floatValue());
      boolean fusing = creeper.method_7007() > 0 || creeper.method_7000();
      if (!fusing
         && dist <= this.reach.get()
         && Look.crosshairOn(creeper)
         && MeteorClient.mc.field_1724.method_7261(0.5F) >= 0.9F
         && this.ticks >= this.nextHit) {
         MeteorClient.mc.field_1761.method_2918(MeteorClient.mc.field_1724, creeper);
         MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
         this.nextHit = this.ticks + 2 + this.random.nextInt(this.extraDelay.get() + 1);
      }

      this.press(MeteorClient.mc.field_1690.field_1894, false);
      this.press(MeteorClient.mc.field_1690.field_1881, dist < 6.5 && this.safeBehind());
   }

   private void retreat() {
      class_1309 threat = this.nearestHostile();
      if (threat != null) {
         class_243 away = MeteorClient.mc
            .field_1724
            .method_33571()
            .method_1023(threat.method_23317(), MeteorClient.mc.field_1724.method_23320(), threat.method_23321())
            .method_1029()
            .method_1021(4.0);
         Look.lookAt(MeteorClient.mc.field_1724.method_33571().method_1019(away), this.aimSpeed.get().floatValue());
      }

      boolean go = this.safeAhead();
      this.press(MeteorClient.mc.field_1690.field_1894, go);
      this.press(MeteorClient.mc.field_1690.field_1867, go);
   }

   private class_1309 nearestHostile() {
      class_1309 best = null;
      double bestDist = 16.0;

      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1569 && e instanceof class_1309 l && l.method_5805()) {
            double d = MeteorClient.mc.field_1724.method_5739(e);
            if (d < bestDist) {
               bestDist = d;
               best = l;
            }
         }
      }

      return best;
   }

   private void scanAware() {
      this.aware.clear();
      double r = this.awareRange.get().intValue();

      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1308 mob && mob.method_5805() && !this.isFoodAnimal(e) && !(this.weight(e) <= 0.0)) {
            double d = MeteorClient.mc.field_1724.method_5739(e);
            if (!(d > r) && mob.method_6057(MeteorClient.mc.field_1724)) {
               boolean onto = d < 6.0
                  || mob.method_5968() == MeteorClient.mc.field_1724
                  || this.movingToward(mob)
                  || this.facingUs(mob)
                  || mob.method_6115()
                  || mob instanceof class_1548 c && c.method_7007() > 0;
               if (onto) {
                  this.aware.add(mob);
               }
            }
         }
      }

      if (!this.aware.isEmpty() && !this.fleeing && this.ticks - this.lastNotice > 400) {
         this.lastNotice = this.ticks;
         ChatUtils.infoPrefix("Farm Builder", "%d mob%s spotted you.", this.aware.size(), this.aware.size() == 1 ? "" : "s");
      }
   }

   private boolean movingToward(class_1308 mob) {
      class_243 v = mob.method_18798();
      double vx = v.field_1352;
      double vz = v.field_1350;
      double vl = Math.sqrt(vx * vx + vz * vz);
      if (vl < 0.02) {
         return false;
      } else {
         double tx = MeteorClient.mc.field_1724.method_23317() - mob.method_23317();
         double tz = MeteorClient.mc.field_1724.method_23321() - mob.method_23321();
         double tl = Math.sqrt(tx * tx + tz * tz);
         return tl > 0.0 && (vx * tx + vz * tz) / (vl * tl) > 0.7;
      }
   }

   private boolean facingUs(class_1308 mob) {
      double rad = Math.toRadians(mob.method_5791());
      double fx = -Math.sin(rad);
      double fz = Math.cos(rad);
      double tx = MeteorClient.mc.field_1724.method_23317() - mob.method_23317();
      double tz = MeteorClient.mc.field_1724.method_23321() - mob.method_23321();
      double tl = Math.sqrt(tx * tx + tz * tz);
      return tl > 0.0 && (fx * tx + fz * tz) / tl > 0.85;
   }

   private boolean shouldFlee() {
      if (this.flee.get() && !this.aware.isEmpty()) {
         int n = this.aware.size();
         float hp = MeteorClient.mc.field_1724.method_6032();
         boolean armed = WEAPONS.stream().anyMatch(w -> this.findInInventory(st -> st.method_7909() == w) >= 0);
         boolean fusingNear = this.aware
            .stream()
            .anyMatch(e -> e instanceof class_1548 c && c.method_7007() > 0 && MeteorClient.mc.field_1724.method_5739(c) < 4.0F);
         return this.fleeCount.get() > 0 && n >= this.fleeCount.get()
            || hp <= this.retreatHealth.get() + 4 && n >= 2
            || hp <= this.retreatHealth.get().intValue()
            || !armed && n >= 2
            || fusingNear && n >= 2;
      } else {
         return false;
      }
   }

   private boolean startFlee() {
      IBaritone b = baritone();
      if (b == null) {
         return false;
      } else {
         this.releaseKeys();
         this.fleeing = true;
         this.fighting = true;
         this.fleeStart = this.ticks;
         this.fleeSafeSince = -1;
         this.setFleeGoal(b);
         ChatUtils.warningPrefix("Farm Builder", "Too dangerous (%d mobs, %.0f hp), running.", this.aware.size(), MeteorClient.mc.field_1724.method_6032());
         return true;
      }
   }

   private void setFleeGoal(IBaritone b) {
      if (!this.aware.isEmpty()) {
         class_2338[] from = this.aware.stream().map(class_1297::method_24515).toArray(class_2338[]::new);
         b.getCustomGoalProcess().setGoalAndPath(new GoalRunAway(20.0, from));
         this.nextFleeGoal = this.ticks + 40;
      }
   }

   private boolean tickFlee() {
      IBaritone b = baritone();
      boolean threatNear = this.aware.stream().anyMatch(ex -> MeteorClient.mc.field_1724.method_5739(ex) < 12.0F);
      if (threatNear) {
         this.fleeSafeSince = -1;
      } else if (this.fleeSafeSince < 0) {
         this.fleeSafeSince = this.ticks;
      }

      if (b != null && (this.fleeSafeSince < 0 || this.ticks - this.fleeSafeSince <= 60) && this.ticks - this.fleeStart <= 600) {
         if (this.ticks >= this.nextFleeGoal) {
            this.setFleeGoal(b);
         }

         for (class_1309 e : this.aware) {
            if (MeteorClient.mc.field_1724.method_5739(e) <= this.reach.get()
               && Look.crosshairOn(e)
               && MeteorClient.mc.field_1724.method_7261(0.5F) >= 0.95F
               && this.ticks >= this.nextHit) {
               MeteorClient.mc.field_1761.method_2918(MeteorClient.mc.field_1724, e);
               MeteorClient.mc.field_1724.method_6104(class_1268.field_5808);
               this.nextHit = this.ticks + 2 + this.random.nextInt(this.extraDelay.get() + 1);
               break;
            }
         }

         return true;
      } else {
         this.fleeing = false;
         this.fighting = false;
         this.fleeEnded = true;
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         ChatUtils.infoPrefix("Farm Builder", "Safe again, back to work.");
         return false;
      }
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   private boolean manageOffhand(float health) {
      if (!this.totem.get() || MeteorClient.mc.field_1724.method_6079().method_7909() == class_1802.field_8288) {
         return false;
      } else if (health > this.retreatHealth.get() + 2) {
         return false;
      } else {
         int slot = this.findInInventory(s -> s.method_7909() == class_1802.field_8288);
         if (slot >= 0 && !this.hostileWithin(2.0) && this.ticks - this.lastTotemTry >= 100) {
            this.lastTotemTry = this.ticks;
            if (!Look.openInventoryIfClosed()) {
               return false;
            } else {
               MeteorClient.mc
                  .field_1761
                  .method_2906(
                     MeteorClient.mc.field_1724.field_7498.field_7763,
                     Look.inventorySlotToHandlerSlot(slot),
                     40,
                     class_1713.field_7791,
                     MeteorClient.mc.field_1724
                  );
               this.invCloseAt = this.ticks + 3 + this.random.nextInt(4);
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private boolean manageWeapon() {
      // The best weapon owned wins: when it sits in the main inventory, fetch it while no mob is close,
      // otherwise fight with the best one already on the hotbar.
      for (class_1792 w : WEAPONS) {
         int slot = this.findInInventory(s -> s.method_7909() == w);
         if (slot < 0) continue;
         if (slot >= 9 && this.ticks - this.lastWeaponFetch >= 600 && !this.hostileWithin(6.0)) {
            if (!Look.openInventoryIfClosed()) {
               return false;
            }

            Look.clickSwapToHotbar(slot, 1);
            this.lastWeaponFetch = this.ticks;
            this.invCloseAt = this.ticks + 3 + this.random.nextInt(4);
            return true;
         }
         break;
      }

      for (class_1792 w : WEAPONS) {
         if (Look.selectHotbar(s -> s.method_7909() == w)) {
            return false;
         }
      }

      return false;
   }

   private int findInInventory(Predicate<class_1799> match) {
      class_1661 inv = MeteorClient.mc.field_1724.method_31548();

      for (int i = 0; i < 36; i++) {
         if (match.test(inv.method_5438(i))) {
            return i;
         }
      }

      return -1;
   }

   private boolean safeAhead() {
      return this.groundAt(MeteorClient.mc.field_1724.method_36454());
   }

   private boolean safeBehind() {
      return this.groundAt(MeteorClient.mc.field_1724.method_36454() + 180.0F);
   }

   private boolean groundAt(float yaw) {
      return Look.groundToward(yaw);
   }

   private void press(class_304 key, boolean down) {
      if (down) {
         this.held.add(key);
      } else if (!this.held.remove(key)) {
         return;
      }

      Look.setKey(key, down);
   }

   private void tapJump() {
      this.press(MeteorClient.mc.field_1690.field_1903, true);
      this.jumpUntil = this.ticks + 2;
   }

   private void releaseKeys() {
      for (class_304 k : this.held) {
         Look.setKey(k, false);
      }

      this.held.clear();
   }

   private boolean stop() {
      this.releaseKeys();
      this.fighting = false;
      this.target = null;
      this.critPhase = 0;
      return false;
   }
}
