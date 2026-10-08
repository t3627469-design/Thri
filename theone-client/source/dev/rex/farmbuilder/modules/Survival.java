package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1569;
import net.minecraft.class_1661;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_243;
import net.minecraft.class_4174;
import net.minecraft.class_418;
import net.minecraft.class_9334;

final class Survival {
   private static final Set<class_1792> NEVER_EAT = Set.of(
      class_1802.field_8680, class_1802.field_8635, class_1802.field_8323, class_1802.field_8233, class_1802.field_8766
   );
   private static final Set<class_1792> LAST_RESORT = Set.of(class_1802.field_8511, class_1802.field_8726);
   private static final Set<class_1792> JUNK = Set.of(
      class_1802.field_20412,
      class_1802.field_29025,
      class_1802.field_28866,
      class_1802.field_8831,
      class_1802.field_8460,
      class_1802.field_8110,
      class_1802.field_20394,
      class_1802.field_20401,
      class_1802.field_20407,
      class_1802.field_27021,
      class_1802.field_27020,
      class_1802.field_8328,
      class_1802.field_8858,
      class_1802.field_8200,
      class_1802.field_20384,
      class_1802.field_8145,
      class_1802.field_8511,
      class_1802.field_8680,
      class_1802.field_8606,
      class_1802.field_8276,
      class_1802.field_8107,
      class_1802.field_8317,
      class_1802.field_8635,
      class_1802.field_17532,
      class_1802.field_8794,
      class_1802.field_17535,
      class_1802.field_17536,
      class_1802.field_17537,
      class_1802.field_17538,
      class_1802.field_17539,
      class_1802.field_17540,
      class_1802.field_42688,
      class_1802.field_37508,
      class_1802.field_8600,
      class_1802.field_8279
   );
   private final Setting<Boolean> autoEat;
   private final Setting<Integer> eatHunger;
   private final Setting<Integer> maxDeaths;
   private final Setting<Boolean> huntForFood;
   private final Setting<Boolean> throwJunk;
   private final Combat combat;
   private final Random random = new Random();
   private Survival.Task task = Survival.Task.NONE;
   private final WaterRecovery breathing = new WaterRecovery();
   private int breatheCooldownUntil;
   private int ticks;
   private int taskStart;
   private int nextAction;
   private int startFood;
   private boolean dead;
   private int respawnAt;
   private boolean respawned;
   private int deaths;
   private class_2338 lastSafe;
   private int huntStart;
   private int huntCooldownUntil;
   private int nextRetarget;
   private boolean walking;
   private java.util.function.IntSupplier reserveItems = () -> 0;
   private int huntReserve;
   private boolean resumeWork;
   private java.util.function.BiConsumer<class_1792, class_2338> discarded = (item, at) -> {};

   Survival(Settings settings, Combat combat) {
      this.combat = combat;
      SettingGroup sg = settings.createGroup("Survival");
      this.autoEat = sg.add(new Builder().name("auto-eat").description("Eat when hungry or hurt.").defaultValue(true).build());
      this.eatHunger = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("eat-hunger")
            .description("Eat at or below this food level (20 = full).")
            .defaultValue(14)
            .range(1, 19)
            .sliderRange(1, 19)
            .build()
      );
      this.huntForFood = sg.add(
         new Builder().name("hunt-for-food").description("Out of food: go kill cows/pigs/chickens/sheep and eat that.").defaultValue(true).build()
      );
      this.throwJunk = sg.add(
         new Builder().name("throw-junk").description("Drop cobble/dirt/gravel etc. when the inventory fills up.").defaultValue(true).build()
      );
      this.maxDeaths = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-deaths")
            .description("Stop after this many deaths. 0 = never stop.")
            .defaultValue(3)
            .range(0, 50)
            .sliderRange(0, 10)
            .build()
      );
   }

   void setFoodReserve(java.util.function.IntSupplier reserve) { this.reserveItems = reserve; }
   void setNoBreak(java.util.function.Predicate<class_2338> blocked) { this.breathing.setNoBreak(blocked); }
   boolean isBreathing() { return this.task == Task.BREATHE; }
   void setDiscardListener(java.util.function.BiConsumer<class_1792, class_2338> listener) { this.discarded = listener; }
   boolean consumeWorkResume() { boolean resume = this.resumeWork; this.resumeWork = false; return resume; }

   static boolean ordinaryFood(class_1799 stack) {
      return stack != null && !stack.method_7960() && stack.method_58694(class_9334.field_50075) != null
         && !NEVER_EAT.contains(stack.method_7909()) && !LAST_RESORT.contains(stack.method_7909());
   }

   int foodCount() {
      if (MeteorClient.mc.field_1724 == null) return 0;
      int count = 0;
      for (int i = 0; i < 36; i++) {
         class_1799 stack = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (ordinaryFood(stack)) count += stack.method_7947();
      }
      class_1799 offhand = MeteorClient.mc.field_1724.method_6079();
      if (ordinaryFood(offhand)) count += offhand.method_7947();
      return count;
   }

   int deaths() {
      return this.deaths;
   }

   boolean tooManyDeaths() {
      return this.maxDeaths.get() > 0 && this.deaths >= this.maxDeaths.get();
   }

   String status() {
      return switch (this.task) {
         case EAT -> "Eating";
         case SWAP_FOOD -> "Grabbing food from the inventory";
         case BREATHE -> "Swimming up for air";
         case ESCAPE -> "Getting out of lava/fire";
         case HUNT -> this.walking ? "Walking to food (animals/drops)" : "Hunting for food";
         case ROOM -> "Throwing out junk";
         default -> null;
      };
   }

   boolean isBusy() {
      return this.task != Survival.Task.NONE;
   }

   boolean needsBaritone() {
      return this.task == Survival.Task.HUNT && this.walking;
   }

   boolean consumeRespawned() {
      boolean r = this.respawned;
      this.respawned = false;
      return r;
   }

   void reset() {
      if (this.task != Survival.Task.NONE) {
         Look.releaseMovementKeys();
      }

      if (this.task == Survival.Task.HUNT) {
         this.stopHunt();
      }

      if (this.task == Survival.Task.ROOM || this.task == Survival.Task.SWAP_FOOD) {
         Look.closeScreen();
      }

      this.task = Survival.Task.NONE;
      this.dead = false;
      this.respawned = false;
      this.resumeWork = false;
      this.huntReserve = 0;
      this.breathing.reset();
      this.breatheCooldownUntil = 0;
   }

   boolean handleDeathScreen() {
      this.ticks++;
      boolean onDeathScreen = MeteorClient.mc.field_1755 instanceof class_418
         || MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1724.method_29504();
      if (!onDeathScreen) {
         this.dead = false;
         return false;
      } else {
         if (!this.dead) {
            this.dead = true;
            this.deaths++;
            this.respawnAt = this.ticks + 60 + this.random.nextInt(100);
            if (this.task != Survival.Task.NONE) {
               Look.releaseMovementKeys();
               if (this.task == Survival.Task.HUNT) {
                  this.stopHunt();
               }

               this.task = Survival.Task.NONE;
            }

            this.combat.reset();
            ChatUtils.warningPrefix("Farm Builder", "Died (%d so far). Respawning shortly.", this.deaths);
         }

         if (this.ticks >= this.respawnAt && MeteorClient.mc.field_1724 != null) {
            MeteorClient.mc.field_1724.method_7331();
            MeteorClient.mc.method_1507(null);
            this.respawned = true;
            this.respawnAt = this.ticks + 100;
         }

         return true;
      }
   }

   static boolean emergencyDuringCleanup(boolean lava, boolean fire, boolean water, boolean submerged, int air) {
      return lava || fire && !water || submerged && air < 120;
   }

   boolean tick() {
      if (MeteorClient.mc.field_1724 == null) {
         return false;
      } else {
         if (MeteorClient.mc.field_1724.method_24828()
            && !MeteorClient.mc.field_1724.method_5771()
            && !MeteorClient.mc.field_1724.method_5809()
            && this.ticks % 20 == 0) {
            this.lastSafe = MeteorClient.mc.field_1724.method_24515();
         }

         if (this.task == Task.ROOM && emergencyDuringCleanup(MeteorClient.mc.field_1724.method_5771(),
            MeteorClient.mc.field_1724.method_5809(), MeteorClient.mc.field_1724.method_5799(),
            MeteorClient.mc.field_1724.method_5869(), MeteorClient.mc.field_1724.method_5669())) {
            Look.closeScreen();
            this.task = Task.NONE;
         }
         switch (this.task) {
            case NONE:
               this.pickTask();
               break;
            case EAT:
               this.tickEat();
               break;
            case SWAP_FOOD:
               this.tickSwapFood();
               break;
            case BREATHE:
               this.tickBreathe();
               break;
            case ESCAPE:
               this.tickEscape();
               break;
            case HUNT:
               this.tickHunt();
            case ROOM:
         }

         return this.task != Survival.Task.NONE && this.task != Survival.Task.ROOM;
      }
   }

   private void pickTask() {
      if (MeteorClient.mc.field_1755 == null) {
         if (!MeteorClient.mc.field_1724.method_5771() && (!MeteorClient.mc.field_1724.method_5809() || MeteorClient.mc.field_1724.method_5799())) {
            if (MeteorClient.mc.field_1724.method_5869() && MeteorClient.mc.field_1724.method_5669() < 120 && this.ticks >= this.breatheCooldownUntil) {
               this.begin(Survival.Task.BREATHE);
            } else if (!this.combat.isFighting() && !this.hostileWithin(8.0)) {
               int food = MeteorClient.mc.field_1724.method_7344().method_7586();
               boolean hungry = food <= this.eatHunger.get() || MeteorClient.mc.field_1724.method_6032() <= 14.0F && food < 20;
               if (this.autoEat.get() && hungry) {
                  int slot = this.bestFood(food <= 6);
                  if (slot >= 0) {
                     this.startFood = food;
                     this.begin(slot < 9 ? Survival.Task.EAT : Survival.Task.SWAP_FOOD);
                  } else {
                     if (this.huntForFood.get() && food <= 8 && this.ticks >= this.huntCooldownUntil) {
                        this.huntReserve = 0;
                        this.combat.setHunting(true);
                        this.huntStart = this.ticks;
                        this.nextRetarget = 0;
                        this.begin(Survival.Task.HUNT);
                        ChatUtils.infoPrefix("Farm Builder", "Out of food, hunting.");
                     }
                  }
               }
               if (this.task == Task.NONE && this.huntForFood.get() && this.reserveItems.getAsInt() > 0
                  && this.foodCount() < this.reserveItems.getAsInt() && this.ticks >= this.huntCooldownUntil
                  && MeteorClient.mc.field_1724.method_31548().method_7376() != -1) {
                  this.huntReserve = this.reserveItems.getAsInt();
                  this.combat.setHunting(true);
                  this.huntStart = this.ticks;
                  this.nextRetarget = 0;
                  this.begin(Task.HUNT);
                  ChatUtils.infoPrefix("Farm Builder", "Food reserve low; gathering up to %d ordinary food items.", this.huntReserve);
               }
            }
         } else {
            this.begin(Survival.Task.ESCAPE);
         }
      }
   }

   private void begin(Survival.Task t) {
      this.task = t;
      this.taskStart = this.ticks;
      this.nextAction = this.ticks + 2 + this.random.nextInt(4);
   }

   private void finish() {
      this.breathing.release();
      Look.releaseMovementKeys();
      this.task = Survival.Task.NONE;
   }

   private void tickEat() {
      int food = MeteorClient.mc.field_1724.method_7344().method_7586();
      if (food <= this.startFood && this.ticks - this.taskStart <= 60 && !this.combat.isFighting() && MeteorClient.mc.field_1755 == null) {
         int slot = this.bestFood(food <= 6);
         if (slot >= 0 && slot < 9) {
            class_1799 want = MeteorClient.mc.field_1724.method_31548().method_5438(slot);
            Look.selectHotbar(s -> s.method_7909() == want.method_7909());
            if (this.ticks >= this.nextAction) {
               Look.setKey(MeteorClient.mc.field_1690.field_1904, true);
            }
         } else {
            this.finish();
         }
      } else {
         Look.setKey(MeteorClient.mc.field_1690.field_1904, false);
         this.finish();
      }
   }

   private void tickSwapFood() {
      if (this.ticks >= this.nextAction) {
         int slot = this.bestFood(MeteorClient.mc.field_1724.method_7344().method_7586() <= 6);
         if (slot < 9) {
            Look.closeScreen();
            this.begin(slot >= 0 ? Survival.Task.EAT : Survival.Task.NONE);
         } else if (!Look.openInventoryIfClosed()) {
            this.finish();
         } else {
            Look.clickSwapToHotbar(slot, 7);
            this.nextAction = this.ticks + 3 + this.random.nextInt(4);
         }
      }
   }

   private int bestFood(boolean desperate) {
      class_1661 inv = MeteorClient.mc.field_1724.method_31548();
      int best = -1;
      float bestScore = 0.0F;

      for (int i = 0; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         class_4174 f = st.method_58694(class_9334.field_50075);
         if (f != null && !NEVER_EAT.contains(st.method_7909()) && (!LAST_RESORT.contains(st.method_7909()) || desperate)) {
            float score = f.comp_2491() + f.comp_2492() - (LAST_RESORT.contains(st.method_7909()) ? 10 : 0) + (i < 9 ? 0.5F : 0.0F);
            if (best < 0 || score > bestScore) {
               best = i;
               bestScore = score;
            }
         }
      }

      return best;
   }

   boolean hasFood() {
      return this.bestFood(true) >= 0;
   }

   private void tickBreathe() {
      if (!MeteorClient.mc.field_1724.method_5869()
         || MeteorClient.mc.field_1724.method_5669() >= MeteorClient.mc.field_1724.method_5748() - 20
         || this.ticks - this.taskStart > 200) {
         if (MeteorClient.mc.field_1724.method_5869()) {
            this.breathing.abort(this.ticks);
            this.breatheCooldownUntil = this.ticks + 60;
            this.resumeWork = true;
         }
         this.finish();
         return;
      }
      if (this.breathing.active() || this.breathing.begin(baritone(), this.ticks, true)) {
         this.breathing.clear(this.ticks, true);
      } else {
         class_2338 gap = WaterRecovery.airGap();
         Look.setKey(MeteorClient.mc.field_1690.field_1894, gap != null);
         if (gap != null) {
            class_243 eye = MeteorClient.mc.field_1724.method_33571();
            Look.lookAt(new class_243(gap.method_10263() + 0.5, eye.field_1351, gap.method_10260() + 0.5), 18.0F);
         }
         Look.setKey(MeteorClient.mc.field_1690.field_1903, true);
      }
   }

   private void tickEscape() {
      if (this.lastSafe != null) {
         Look.lookAt(class_243.method_24955(this.lastSafe), 20.0F);
      }

      Look.setKey(MeteorClient.mc.field_1690.field_1903, true);
      Look.setKey(MeteorClient.mc.field_1690.field_1894, true);
      boolean out = !MeteorClient.mc.field_1724.method_5771() && (!MeteorClient.mc.field_1724.method_5809() || MeteorClient.mc.field_1724.method_5799());
      if (out || this.ticks - this.taskStart > 200) {
         this.finish();
      }
   }

   private void tickHunt() {
      if (this.huntReserve > 0 ? this.foodCount() >= this.huntReserve
         || this.hasFood() && MeteorClient.mc.field_1724.method_7344().method_7586() <= 8 : this.hasFood()) {
         this.stopHunt();
         this.finish();
      } else if (this.ticks - this.huntStart > 3600) {
         ChatUtils.warningPrefix("Farm Builder", "No animals found to eat, trying again in a few minutes.");
         this.huntCooldownUntil = this.ticks + 6000;
         this.stopHunt();
         this.finish();
      } else if (this.combat.isFighting()) {
         this.walking = false;
      } else if (this.ticks >= this.nextRetarget) {
         this.nextRetarget = this.ticks + 40;
         IBaritone b = baritone();
         if (b != null) {
            class_1297 drop = this.nearestFoodDrop();
            class_1297 goal = drop != null ? drop : this.combat.huntTarget();
            if (goal == null) {
               this.walking = false;
               if (this.ticks - this.huntStart > 200) {
                  this.huntCooldownUntil = this.ticks + 6000;
                  this.stopHunt();
                  this.finish();
                  ChatUtils.infoPrefix("Farm Builder", "No nearby food source; continuing work and checking the reserve later.");
               }
            } else {
               if (MeteorClient.mc.field_1724.method_5739(goal) > (drop != null ? 0.8 : 2.5)) {
                  b.getCustomGoalProcess().setGoalAndPath(new GoalNear(goal.method_24515(), drop != null ? 0 : 1));
                  this.walking = true;
               }
            }
         }
      }
   }

   private class_1297 nearestFoodDrop() {
      class_1297 best = null;
      double bestDist = 10.0;

      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1542 item && ordinaryFood(item.method_6983())) {
            double d = MeteorClient.mc.field_1724.method_5739(e);
            if (d < bestDist) {
               bestDist = d;
               best = e;
            }
         }
      }

      return best;
   }

   private void stopHunt() {
      this.combat.setHunting(false);
      if (this.walking) {
         IBaritone b = baritone();
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }
      }

      this.walking = false;
      this.resumeWork = true;
   }

   boolean hasDiscardableJunk(Predicate<class_1792> keep) {
      if (!this.throwJunk.get() || MeteorClient.mc.field_1724 == null) return false;
      class_1661 inv = MeteorClient.mc.field_1724.method_31548();
      for (int i = 0; i < 36; i++) {
         class_1799 stack = inv.method_5438(i);
         if (!stack.method_7960() && i != inv.method_67532() && JUNK.contains(stack.method_7909()) && !keep.test(stack.method_7909())) return true;
      }
      return false;
   }

   boolean makeRoom(int freeSlots, Predicate<class_1792> keep) {
      if (this.throwJunk.get() && MeteorClient.mc.field_1724 != null) {
         if (this.task != Survival.Task.NONE && this.task != Survival.Task.ROOM) {
            return false;
         } else {
            int junkSlot = -1;
            int empty = 0;
            class_1661 inv = MeteorClient.mc.field_1724.method_31548();
            int selected = inv.method_67532();

            for (int i = 0; i < 36; i++) {
               class_1799 st = inv.method_5438(i);
               if (st.method_7960()) {
                  empty++;
               } else if (junkSlot < 0 && i != selected && JUNK.contains(st.method_7909()) && !keep.test(st.method_7909())) {
                  junkSlot = i;
               }
            }

            if (empty < freeSlots && junkSlot >= 0) {
               if (this.task != Survival.Task.ROOM) {
                  this.begin(Survival.Task.ROOM);
               }

               if (this.ticks < this.nextAction) {
                  return false;
               } else if (!Look.openInventoryIfClosed()) {
                  return false;
               } else {
                  class_1792 discardedItem = inv.method_5438(junkSlot).method_7909();
                  MeteorClient.mc
                     .field_1761
                     .method_2906(
                        MeteorClient.mc.field_1724.field_7498.field_7763,
                        Look.inventorySlotToHandlerSlot(junkSlot),
                        1,
                        class_1713.field_7795,
                        MeteorClient.mc.field_1724
                     );
                  this.discarded.accept(discardedItem, MeteorClient.mc.field_1724.method_24515());
                  this.nextAction = this.ticks + 8;
                  return false;
               }
            } else {
               if (this.task == Survival.Task.ROOM) {
                  Look.closeScreen();
                  this.task = Survival.Task.NONE;
               }

               return true;
            }
         }
      } else {
         return true;
      }
   }

   private boolean hostileWithin(double r) {
      for (class_1297 e : MeteorClient.mc.field_1687.method_18112()) {
         if (e instanceof class_1569 && e.method_5805() && MeteorClient.mc.field_1724.method_5739(e) <= r) {
            return true;
         }
      }

      return false;
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   private static enum Task {
      NONE,
      EAT,
      SWAP_FOOD,
      BREATHE,
      ESCAPE,
      HUNT,
      ROOM;
   }
}
