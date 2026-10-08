package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.path.IPathExecutor;
import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.class_1799;
import net.minecraft.class_2338;

final class Movement {
   private final Setting<Integer> stuckSeconds;
   private final Setting<Boolean> humanPathing;
   private final Setting<String> unstuckCommand;
   private final Setting<Integer> maxRecoveries;
   private final Setting<Boolean> fastPathing;
   private boolean cautious;
   private final WaterRecovery water = new WaterRecovery();
   private final Deque<Integer> recoveryTimes = new ArrayDeque<>();
   private int ticks;
   private int activeTicks;
   private int lastProgress;
   private int anchorSince;
   private class_2338 anchor;
   private int invHash;
   private int pathPos = -1;
   private Object sampledPath;
   private int notPathingSince = -1;
   private int level;
   private int lastStuckTick = -100000;
   private Movement.Action action = Movement.Action.NONE;
   private int actionEnd;
   private boolean pendingReissue;
   private String reason = "";
   private class_2338 jumpSource;
   private class_2338 jumpDestination;
   private int jumpSince = -1;
   private boolean jumpAirborne;
   private class_2338 breakingBlock;
   private int breakingSince;

   Movement(Settings settings) {
      SettingGroup sg = settings.createGroup("Movement");
      this.humanPathing = sg.add(
         new Builder()
            .name("human-pathing")
            .description("No parkour/bucket tricks, camera follows Baritone's real rotations, tools saved before they break.")
            .defaultValue(true)
            .build()
      );
      this.stuckSeconds = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("stuck-seconds")
            .description("Seconds without progress before it counts as stuck.")
            .defaultValue(12)
            .range(4, 120)
            .sliderRange(5, 60)
            .build()
      );
      this.unstuckCommand = sg.add(
         new meteordevelopment.meteorclient.settings.StringSetting.Builder()
            .name("unstuck-command")
            .description("Last-resort command when nothing else frees it, e.g. /home farm. Empty = skip.")
            .defaultValue("")
            .build()
      );
      this.fastPathing = sg.add(
         new Builder()
            .name("fast-pathing")
            .description("Faster path planning. Parkour and diagonal overshoot are only enabled with human-pathing off.")
            .defaultValue(true)
            .build()
      );
      this.maxRecoveries = sg.add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-recoveries")
            .description("Give up after this many stuck fixes in 10 minutes.")
            .defaultValue(6)
            .range(1, 50)
            .sliderRange(2, 20)
            .build()
      );
   }

   private final java.util.Map<baritone.api.Settings.Setting<?>, Object> savedSettings = new java.util.IdentityHashMap<>();

   void captureBaritoneSettings() {
      if (this.savedSettings.isEmpty()) {
         for (baritone.api.Settings.Setting<?> setting : BaritoneAPI.getSettings().allSettings)
            this.savedSettings.put(setting, setting.value);
      }
   }

   void restoreBaritoneSettings() {
      this.savedSettings.forEach(Movement::restoreSetting);
      this.savedSettings.clear();
   }

   @SuppressWarnings("unchecked")
   private static <T> void restoreSetting(baritone.api.Settings.Setting<T> setting, Object value) {
      setting.value = (T) value;
   }

   void applyBaritoneSettings() {
      this.captureBaritoneSettings();
      configureSettings(BaritoneAPI.getSettings(), this.humanPathing.get(), this.fastPathing.get(), this.cautious);
   }

   static void configureSettings(baritone.api.Settings s, boolean humanPathing, boolean fastPathing, boolean cautious) {
      boolean conservative = humanPathing || cautious;
      boolean agile = fastPathing && !conservative;
      s.allowParkour.value = agile;
      s.allowParkourPlace.value = false;
      s.allowParkourAscend.value = agile;
      s.allowWaterBucketFall.value = false;
      s.assumeWalkOnWater.value = false;
      s.assumeStep.value = false;
      s.allowDiagonalDescend.value = agile;
      s.allowOvershootDiagonalDescend.value = agile;
      s.sprintInWater.value = !conservative && fastPathing;
      s.maxFallHeightNoWater.value = conservative ? 2 : 3;
      s.blockReachDistance.value = cautious ? 3.2F : 3.5F;
      s.disconnectOnArrival.value = false;
      s.chatDebug.value = false;
      s.logAsToast.value = false;
      s.echoCommands.value = false;
      s.allowSprint.value = true;
      s.slowPath.value = false;
      s.pathThroughCachedOnly.value = false;
      s.costHeuristic.value = fastPathing ? 3.0 : 3.563;
      s.backtrackCostFavoringCoefficient.value = fastPathing ? 1.0 : 0.5;
      s.antiCheatCompatibility.value = true;
      s.freeLook.value = false;
      s.smoothLook.value = true;
      s.smoothLookTicks.value = conservative ? 7 : 5;
      s.randomLooking.value = 0.0;
      s.itemSaver.value = true;
      s.itemSaverThreshold.value = 10;
      s.autoTool.value = true;
      s.assumeExternalAutoTool.value = false;
      s.avoidance.value = true;
      s.mobAvoidanceRadius.value = 6;
      s.walkWhileBreaking.value = !conservative;
      s.allowInventory.value = true;
      s.primaryTimeoutMS.value = fastPathing ? 2000L : 1000L;
      s.failureTimeoutMS.value = 6000L;
      s.planAheadPrimaryTimeoutMS.value = 2000L;
      s.planAheadFailureTimeoutMS.value = 4000L;
      s.blockPlacementPenalty.value = fastPathing ? 5.0 : 20.0;
      s.blockBreakAdditionalPenalty.value = fastPathing ? 0.5 : 2.0;
      s.inventoryMoveOnlyIfStationary.value = true;
      s.ticksBetweenInventoryMoves.value = 10;
      s.extendCacheOnThreshold.value = true;
      s.maxCachedWorldScanCount.value = 12;
      s.mineMaxOreLocationsCount.value = 48;
      s.mineGoalUpdateInterval.value = 10;
      s.mineScanDroppedItems.value = true;
      s.mineDropLoiterDurationMSThanksLouca.value = 400L;
      s.blacklistClosestOnFailure.value = true;
      s.legitMineIncludeDiagonals.value = true;
      s.allowDiagonalAscend.value = !conservative;
      s.sprintAscends.value = !conservative;
      s.considerPotionEffects.value = true;
      s.movementTimeoutTicks.value = 100;
   }

   boolean isRecovering() {
      return this.action != Movement.Action.NONE;
   }

   boolean needsPause() { return this.action == Action.CLEAR_WATER; }

   void setNoBreak(java.util.function.Predicate<class_2338> blocked) { this.water.setNoBreak(blocked); }

   void suspend() {
      if (this.action == Action.NONE) return;
      if (this.action == Action.RELOCATE) {
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
      }
      this.water.reset();
      Look.releaseMovementKeys();
      Look.stopMining();
      this.action = Action.NONE;
      this.pendingReissue = true;
      this.clearJump();
   }

   String lastReason() {
      return this.reason;
   }

   void setCautious(boolean cautious) {
      this.cautious = cautious;
   }

   boolean isCautious() {
      return this.cautious;
   }

   void reset() {
      if (this.action != Movement.Action.NONE) {
         Look.releaseMovementKeys();
      }

      this.action = Movement.Action.NONE;
      this.pendingReissue = false;
      this.level = 0;
      this.anchor = null;
      this.lastProgress = this.activeTicks;
      this.anchorSince = this.activeTicks;
      this.notPathingSince = -1;
      this.recoveryTimes.clear();
      this.clearJump();
      this.breakingBlock = null;
      this.water.reset();
      this.sampledPath = null;
      this.pathPos = -1;
   }

   Movement.Verdict tick(boolean expectProgress) {
      this.ticks++;
      if (MeteorClient.mc.field_1724 == null) {
         return Movement.Verdict.OK;
      } else if (this.action != Movement.Action.NONE) {
         return this.runAction();
      } else if (this.pendingReissue) {
         this.pendingReissue = false;
         this.markProgress();
         return Movement.Verdict.REISSUE;
      } else if (!expectProgress) {
         return Movement.Verdict.OK;
      } else {
         IBaritone b = baritone();
         boolean trying = b != null && (b.getPathingBehavior().isPathing()
            || b.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.JUMP));
         if (Look.isBreaking(b) && !MeteorClient.mc.field_1724.method_5799()) trying = false;
         if (this.water.stalled(this.ticks, MeteorClient.mc.field_1724.method_23317(), MeteorClient.mc.field_1724.method_23321(),
            MeteorClient.mc.field_1724.method_5799(), trying)) {
            if (this.water.begin(b, this.ticks, false)) {
               this.reason = "clearing a block obstructing the water route";
               this.action = Action.CLEAR_WATER;
               return Verdict.RECOVERING;
            }
            if (this.ticks % 40 == 0) return this.onStuck("blocked water route");
         }
         if (this.failedJump()) return this.onStuck("failed jump; choosing another route");
         this.activeTicks++;
         if (this.activeTicks % 10 == 0) {
            this.sample();
         }

         String problem = null;
         int stuckTicks = this.stuckSeconds.get() * 20;
         if (MeteorClient.mc.field_1724.method_5757()) {
            problem = "stuck inside a block";
         } else if (this.activeTicks - this.lastProgress > stuckTicks) {
            problem = "not moving for " + this.stuckSeconds.get() + "s";
         } else if (this.activeTicks - this.anchorSince > 1200) {
            problem = "walking in circles";
         } else if (this.notPathingSince >= 0 && this.activeTicks - this.notPathingSince > stuckTicks * 2) {
            problem = "Baritone can't find a path";
         }

         if (problem == null) {
            if (this.level > 0 && this.ticks - this.lastStuckTick > 2400) {
               this.level = 0;
            }

            return Movement.Verdict.OK;
         } else {
            return this.onStuck(problem);
         }
      }
   }

   private void clearJump() {
      this.jumpSource = null;
      this.jumpDestination = null;
      this.jumpSince = -1;
      this.jumpAirborne = false;
   }

   boolean waitForDigging(IBaritone b) {
      if (!Look.isBreaking(b)) return false;
      this.clearJump();
      return true;
   }

   private boolean failedJump() {
      if (MeteorClient.mc.field_1724.method_5799()) {
         this.clearJump();
         return false;
      }
      IBaritone b = baritone();
      if (this.waitForDigging(b)) return false;
      IPathExecutor exec = b == null ? null : b.getPathingBehavior().getCurrent();
      if (exec == null || exec.getPosition() < 0 || exec.getPosition() >= exec.getPath().movements().size()) {
         this.clearJump();
         return false;
      }
      baritone.api.pathing.movement.IMovement move = exec.getPath().movements().get(exec.getPosition());
      String type = move.getClass().getSimpleName();
      if (!type.contains("Parkour") && !type.contains("Ascend")) {
         this.clearJump();
         return false;
      }
      if (!move.getSrc().equals(this.jumpSource) || !move.getDest().equals(this.jumpDestination)) {
         this.clearJump();
         this.jumpSource = move.getSrc();
         this.jumpDestination = move.getDest();
      }
      boolean requested = b.getInputOverrideHandler().isInputForcedDown(baritone.api.utils.input.Input.JUMP);
      if (requested && this.jumpSince < 0) this.jumpSince = this.ticks;
      if (this.jumpSince < 0) return false;
      boolean grounded = MeteorClient.mc.field_1724.method_24828();
      if (!grounded) this.jumpAirborne = true;
      return FarmLogic.failedJump(grounded, this.jumpAirborne,
         MeteorClient.mc.field_1724.method_23318() < this.jumpSource.method_10264() - 0.5,
         this.ticks - this.jumpSince,
         MeteorClient.mc.field_1724.method_24515().equals(this.jumpDestination));
   }

   private Movement.Verdict rerouteJump() {
      this.cautious = true;
      this.applyBaritoneSettings();
      IBaritone b = baritone();
      class_2338 blockedDestination = this.jumpDestination;
      baritone.api.pathing.goals.Goal previousGoal = b == null ? null : b.getPathingBehavior().getGoal();
      this.clearJump();
      if (b == null || previousGoal == null) return Movement.Verdict.REISSUE;
      b.getPathingBehavior().cancelEverything();
      Look.releaseMovementKeys();
      Look.stopMining();
      class_2338 feet = MeteorClient.mc.field_1724.method_24515();
      java.util.List<class_2338> options = new java.util.ArrayList<>();
      for (int[] offset : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
         class_2338 side = feet.method_10069(offset[0], 0, offset[1]);
         if (side.equals(blockedDestination) || !MeteorClient.mc.field_1687.method_8393(side.method_10263() >> 4, side.method_10260() >> 4)
            || !MeteorClient.mc.field_1687.method_8311(side) || !MeteorClient.mc.field_1687.method_8311(side.method_10084())
            || !MeteorClient.mc.field_1687.method_8316(side.method_10074()).method_15769()
            || MeteorClient.mc.field_1687.method_8320(side.method_10074()).method_26220(MeteorClient.mc.field_1687, side.method_10074()).method_1110()) continue;
         options.add(side);
      }
      class_2338 side = bestRecoveryStep(feet, previousGoal, options);
      if (side != null) {
         b.getCustomGoalProcess().setGoalAndPath(new baritone.api.pathing.goals.GoalBlock(side));
         this.action = Movement.Action.RELOCATE;
         this.actionEnd = this.ticks + 100;
         return Movement.Verdict.RECOVERING;
      }
      return Movement.Verdict.REISSUE;
   }

   static class_2338 bestRecoveryStep(class_2338 feet, baritone.api.pathing.goals.Goal goal, java.util.List<class_2338> options) {
      if (goal == null) return null;
      return options.stream().filter(p -> !p.equals(feet)).min(java.util.Comparator.comparingDouble(p ->
         goal.heuristic(p.method_10263(), p.method_10264(), p.method_10260()) + Math.sqrt(feet.method_10262(p)) * 3.563)).orElse(null);
   }

   private void sample() {
      IBaritone b = baritone();
      class_2338 pos = MeteorClient.mc.field_1724.method_24515();
      if (this.anchor == null || !pos.method_19771(this.anchor, 3.0)) {
         this.anchor = pos;
         this.anchorSince = this.activeTicks;
         this.markProgress();
      }

      int hash = inventoryHash();
      if (hash != this.invHash) {
         this.invHash = hash;
         this.markProgress();
         this.anchorSince = this.activeTicks;
      }

      if (Look.isBreaking(b) && MeteorClient.mc.field_1765 instanceof net.minecraft.class_3965 hit
         && hit.method_17783() == net.minecraft.class_239.class_240.field_1332) {
         if (!hit.method_17777().equals(this.breakingBlock)) {
            this.breakingBlock = hit.method_17777().method_10062();
            this.breakingSince = this.activeTicks;
         }
         if (FarmLogic.breakingProgress(this.activeTicks - this.breakingSince)) this.markProgress();
      } else this.breakingBlock = null;

      if (b != null) {
         IPathExecutor exec = b.getPathingBehavior().getCurrent();
         int p = exec == null ? -1 : exec.getPosition();
         Object path = exec == null ? null : exec.getPath();
         if (path == this.sampledPath && p > this.pathPos) this.markProgress();
         this.sampledPath = path;
         this.pathPos = p;

         if (b.getPathingBehavior().isPathing() || this.breakingBlock != null && FarmLogic.breakingProgress(this.activeTicks - this.breakingSince)) {
            this.notPathingSince = -1;
         } else if (this.notPathingSince < 0) {
            this.notPathingSince = this.activeTicks;
         }
      }
   }

   private void markProgress() {
      this.lastProgress = this.activeTicks;
   }

   private Movement.Verdict onStuck(String problem) {
      this.reason = problem;
      this.lastStuckTick = this.ticks;
      this.recoveryTimes.addLast(this.ticks);

      while (!this.recoveryTimes.isEmpty() && this.ticks - this.recoveryTimes.peekFirst() > 12000) {
         this.recoveryTimes.removeFirst();
      }

      if (this.recoveryTimes.size() > this.maxRecoveries.get()) {
         return Movement.Verdict.GIVE_UP;
      } else {
         this.level++;
         this.markProgress();
         this.anchorSince = this.activeTicks;
         this.notPathingSince = -1;
         info("Stuck (" + problem + "), fix #" + this.level + ".");
         IBaritone miningBaritone = baritone();
         if (this.level <= 3 && miningBaritone != null && miningBaritone.getMineProcess().isActive()) {
            if (problem.startsWith("failed jump") || problem.contains("water")) {
               this.cautious = true;
               this.applyBaritoneSettings();
            }
            this.clearJump();
            miningBaritone.getMineProcess().onTick(true, false);
            Look.stopMining();
            return Movement.Verdict.OK;
         }
         if (problem.startsWith("failed jump")) return this.rerouteJump();
         switch (this.level) {
            case 1:
               return this.rerouteJump();
            case 2:
               return Movement.Verdict.REISSUE;
            case 3:
               return this.rerouteJump();
            case 4:
               if (!this.unstuckCommand.get().isBlank()) {
                  ChatUtils.sendPlayerMsg(this.unstuckCommand.get().trim());
                  this.action = Movement.Action.COMMAND;
                  this.actionEnd = this.ticks + 100;
                  return Movement.Verdict.RECOVERING;
               }

               return Movement.Verdict.GIVE_UP;
            default:
               return Movement.Verdict.GIVE_UP;
         }
      }
   }

   private Movement.Verdict runAction() {
      switch (this.action) {
         case CLEAR_WATER:
            IBaritone waterBaritone = baritone();
            if (waterBaritone != null) waterBaritone.getInputOverrideHandler().clearAllKeys();
            WaterRecovery.Result result = this.water.clear(this.ticks, false);
            if (result == WaterRecovery.Result.RUNNING) return Verdict.RECOVERING;
            this.action = Action.NONE;
            this.markProgress();
            this.clearJump();
            if (result == WaterRecovery.Result.FAILED) return this.onStuck("water obstruction couldn't be cleared");
            return Verdict.OK;
         case RELOCATE:
            IBaritone b = baritone();
            boolean arrived = b == null || !b.getCustomGoalProcess().isActive();
            if (arrived || this.ticks >= this.actionEnd) {
               if (b != null) {
                  b.getPathingBehavior().cancelEverything();
               }

               this.action = Movement.Action.NONE;
               this.pendingReissue = true;
            }
            break;
         case COMMAND:
            if (this.ticks >= this.actionEnd) {
               this.action = Movement.Action.NONE;
               this.pendingReissue = true;
            }
            break;
         default:
            this.action = Movement.Action.NONE;
      }

      return Movement.Verdict.RECOVERING;
   }

   private static int inventoryHash() {
      int h = 1;

      for (int i = 0; i < 36; i++) {
         class_1799 st = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         h = 31 * h + (st.method_7960() ? 0 : st.method_7909().hashCode() * 64 + st.method_7947());
      }

      return h;
   }

   private static void info(String msg) {
      ChatUtils.infoPrefix("Farm Builder", msg);
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   private static enum Action {
      NONE,
      CLEAR_WATER,
      RELOCATE,
      COMMAND;
   }

   static enum Verdict {
      OK,
      RECOVERING,
      REISSUE,
      GIVE_UP;
   }
}
