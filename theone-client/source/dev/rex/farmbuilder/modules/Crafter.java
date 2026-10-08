package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.class_1303;
import net.minecraft.class_1542;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1714;
import net.minecraft.class_1720;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3489;
import net.minecraft.class_490;
import net.minecraft.class_7923;
import net.minecraft.class_9334;

final class Crafter {
   private static final Map<class_1792, Crafter.Ing> EXACT = new HashMap<>();
   private static final Map<Crafter.Ing, Crafter.Source> SOURCES = new HashMap<>();
   private static final Crafter.Ing LOG = new Crafter.Ing("logs", st -> st.method_31573(class_3489.field_15539), class_1802.field_8583);
   private static final Crafter.Ing PLANKS = new Crafter.Ing("planks", st -> st.method_31573(class_3489.field_15537), class_1802.field_8118);
   private static final Crafter.Ing PICK_ANY = pick(
      "a pickaxe",
      class_1802.field_8647,
      class_1802.field_8647,
      class_1802.field_8335,
      class_1802.field_8387,
      class_1802.field_8403,
      class_1802.field_8377,
      class_1802.field_22024
   );
   private static final Crafter.Ing PICK_STONE = pick(
      "a stone pickaxe", class_1802.field_8387, class_1802.field_8387, class_1802.field_8403, class_1802.field_8377, class_1802.field_22024
   );
   private static final Crafter.Ing PICK_IRON = pick(
      "an iron pickaxe", class_1802.field_8403, class_1802.field_8403, class_1802.field_8377, class_1802.field_22024
   );
   private final LinkedHashMap<class_1792, Integer> goals = new LinkedHashMap<>();
   private Predicate<class_1792> alreadyDone = i -> false;
   final Set<class_1792> skipped = new LinkedHashSet<>();
   String failReason;
   private Crafter.Phase phase = Crafter.Phase.DECIDE;
   private int wait;
   private int ticks;
   private final Deque<Runnable> clicks = new ArrayDeque<>();
   private class_1703 clickHandler;
   private int lastSrc = -1;
   private boolean clickAbort;
   private final List<class_2338> stations = new ArrayList<>();
   private class_2338 target;
   private class_2248 targetBlock;
   private class_1792 placeItem;
   private int phaseStart;
   private Crafter.Craft pendingCraft;
   private Crafter.Smelt pendingSmelt;
   private int smeltAmount;
   private int pendingCrafts = 1;
   /** One loaded furnace: where it is, when its batch is done, and how many items it is smelting. */
   private static final class Lane {
      final class_2338 pos;
      final int readyAt;
      final int items;

      Lane(class_2338 pos, int readyAt, int items) {
         this.pos = pos;
         this.readyAt = readyAt;
         this.items = items;
      }
   }

   private final List<Lane> lanes = new ArrayList<>();
   private Lane collectingLane;
   private class_2338 walkBackTo;
   private Crafter.Ing smeltingFor;
   private int lanePlanCount;
   private int lanePlanSize;
   private boolean collecting;
   private Predicate<class_1792> keep = i -> true;
   private class_2338 stash;
   private boolean stashing;
   private int stashFails;
   private static final Set<class_1792> BULK = Set.of(
      class_1802.field_20412,
      class_1802.field_29025,
      class_1802.field_8831,
      class_1802.field_8858,
      class_1802.field_8110,
      class_1802.field_20407,
      class_1802.field_20401,
      class_1802.field_20394,
      class_1802.field_27021,
      class_1802.field_28866,
      class_1802.field_8328
   );
   private static final Map<class_1792, Boolean> USEFUL = new HashMap<>();
   private Crafter.Mine mining;
   private Crafter.Ing miningFor;
   private int mineTarget;
   private int mineLastCount;
   private int mineLastProgress;
   private final MiningWatch miningWatch = new MiningWatch();
   private int mineRestarts;
   private int mineRestartAt;
   private BooleanSupplier nativeOreScan = () -> true;
   private String lastAction;
   private int sameActionCount;
   private int lastActionHave;
   private boolean starterDone;
   private BooleanSupplier roomMaker;
   private BooleanSupplier localStorage = () -> true;
   private IntSupplier mineCeiling = () -> 2031;
   private Predicate<class_2338> noPlace = p -> false;
   private Look.Placement placement;
   private final Set<class_2338> badSpots = new HashSet<>();
   private int spotSince;
   private int asideUntil;
   private boolean useSent;
   private int useSentAt;
   private Crafter.OreFinding oreMode = Crafter.OreFinding.Auto;
   private final Set<Crafter.Ing> xrayFailed = new HashSet<>();
   private boolean mineLegit;
   private BooleanSupplier diamondScanFirst = () -> true;
   private int mineStallSeconds;
   private java.util.function.Supplier<class_2338> mineAnchor = () -> null;
   private Runnable miningProfile = () -> {};
   private int approachStarted;
   private class_2338 workBlock;
   private class_2338 mineApproach;
   private final DescentWatch descent = new DescentWatch();

   private static class_1792 coalItem;
   private static class_1792 charcoalItem;
   private boolean smeltWithCoal;

   /** Coal and charcoal smelt 8 items each (planks only 1.5), so they are preferred when enough is carried. */
   private static boolean isCoal(class_1799 st) {
      if (coalItem == null) {
         coalItem = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654("minecraft:coal"));
         charcoalItem = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654("minecraft:charcoal"));
      }
      if (st == null || st.method_7960()) return false;
      class_1792 item = st.method_7909();
      return item == coalItem || item == charcoalItem;
   }

   private static Crafter.Ing ex(class_1792 item) {
      return EXACT.computeIfAbsent(
         item, i -> new Crafter.Ing(class_7923.field_41178.method_10221(i).method_12832().replace('_', ' '), st -> st.method_7909() == i && FarmLogic.usableGear(st), i)
      );
   }

   private static Crafter.Ing pick(String name, class_1792 canonical, class_1792... ok) {
      Set<class_1792> set = Set.of(ok);
      return new Crafter.Ing(
         name, st -> set.contains(st.method_7909()) && st.method_7936() - st.method_7919() > Math.max(10.0, st.method_7936() * 0.08), canonical
      );
   }

   private static void craft(Crafter.Ing out, int count, String[] rows, Object... key) {
      Map<Character, Crafter.Ing> map = new HashMap<>();

      for (int i = 0; i < key.length; i += 2) {
         Object v = key[i + 1];
         map.put((Character)key[i], v instanceof Crafter.Ing ing ? ing : ex((class_1792)v));
      }

      SOURCES.put(out, new Crafter.Craft(rows, map, count));
   }

   private static void craft(class_1792 out, int count, String[] rows, Object... key) {
      craft(ex(out), count, rows, key);
   }

   private static void smelt(class_1792 out, class_1792 in) {
      SOURCES.put(ex(out), new Crafter.Smelt(ex(in)));
   }

   private static void mine(Crafter.Ing out, int tier, Integer legitY, class_2248... blocks) {
      SOURCES.put(out, new Crafter.Mine(blocks, tier, legitY));
   }

   private static String[] r(String... rows) {
      return rows;
   }

   static boolean knows(class_1792 item) {
      return SOURCES.containsKey(ex(item));
   }

   void start(Map<class_1792, Integer> what, Predicate<class_1792> done, Crafter.OreFinding oreMode, int mineStallSeconds) {
      this.workBlock = null;
      this.stashing = false;
      this.collecting = false;
      this.clickHandler = null;
      this.clickAbort = false;
      this.pendingCraft = null;
      this.pendingSmelt = null;
      this.badSpots.clear();
      this.placement = null;
      this.asideUntil = 0;
      this.useSent = false;
      this.mining = null;
      this.miningFor = null;
      this.mineApproach = null;
      this.mineLegit = false;
      this.goals.clear();
      this.goals.putAll(what);
      this.alreadyDone = done;
      this.oreMode = oreMode;
      this.xrayFailed.clear();
      this.mineRestarts = 0;
      this.mineRestartAt = 0;
      this.mineStallSeconds = mineStallSeconds;
      this.skipped.clear();
      this.stations.clear();
      this.clicks.clear();
      this.failReason = null;
      this.phase = Crafter.Phase.DECIDE;
      this.wait = 0;
      this.lastAction = null;
      this.sameActionCount = 0;
      this.starterDone = false;
   }

   record Job(Map<class_1792, Integer> goals, Predicate<class_1792> done, OreFinding oreMode, int stallSeconds) {
      Job {
         goals = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(goals));
         java.util.Objects.requireNonNull(done);
         java.util.Objects.requireNonNull(oreMode);
      }
   }

   static String encodeGoals(Job job) {
      return job == null ? "" : job.goals().entrySet().stream().map(e -> class_7923.field_41178.method_10221(e.getKey()) + "=" + e.getValue())
         .collect(java.util.stream.Collectors.joining(";"));
   }

   static Map<class_1792, Integer> decodeGoals(String encoded) {
      Map<class_1792, Integer> goals = new LinkedHashMap<>();
      FarmJob.goals(encoded).forEach((id, count) -> {
         class_1792 item = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654(id));
         if (item == null || item == class_1802.field_8162 || !knows(item)) throw new IllegalArgumentException("Unavailable saved recipe: " + id);
         goals.put(item, count);
      });
      return goals;
   }

   static boolean handlerMatches(class_1703 expected, class_1703 current) { return expected != null && expected == current; }

   Job snapshot() {
      return new Job(new LinkedHashMap<>(this.goals), this.alreadyDone, this.oreMode, this.mineStallSeconds);
   }

   void resume(Job job) {
      this.start(job.goals(), job.done(), job.oreMode(), job.stallSeconds());
   }

   void setMineAnchor(java.util.function.Supplier<class_2338> anchor) {
      this.mineAnchor = anchor;
   }

   void stop() {
      IBaritone b = baritone();
      if (b != null) {
         b.getPathingBehavior().cancelEverything();
      }

      this.clicks.clear();
      if (MeteorClient.mc.field_1724 != null && MeteorClient.mc.field_1755 != null) {
         MeteorClient.mc.field_1724.method_7346();
      }
   }

   void setNoPlace(Predicate<class_2338> blocked) {
      this.noPlace = blocked;
   }

   void setMineCeiling(IntSupplier ceiling) {
      this.mineCeiling = ceiling;
   }

   void setKeep(Predicate<class_1792> keep) {
      this.keep = keep;
   }

   class_2338 stash() {
      return this.stash;
   }

   void setStash(class_2338 pos) {
      this.stash = pos;
   }

   void setNativeOreScan(BooleanSupplier enabled) { this.nativeOreScan = enabled; }

   void setMiningProfile(Runnable profile) { this.miningProfile = profile; }

   boolean needsPickup(class_1799 stack) {
      if (stack == null || stack.method_7960()) return false;
      if (this.miningFor != null && (this.phase == Phase.MINE || this.phase == Phase.MINE_APPROACH)
         && this.miningFor.match.test(stack) && count(this.miningFor.match) < this.mineTarget) return true;
      class_1792 item = stack.method_7909();
      int needed = this.goals.getOrDefault(item, 0);
      return needed > 0 && needed > count(ex(item).match) && !this.alreadyDone.test(item);
   }

   List<class_2248> xrayBlocks() {
      return (this.phase == Phase.MINE || this.phase == Phase.MINE_APPROACH) && this.mining != null
         && this.mining.legitY() != null && !this.mineLegit ? List.of(this.mining.blocks()) : List.of();
   }

   void setDiamondScanFirst(BooleanSupplier enabled) { this.diamondScanFirst = enabled; }

   static OreFinding effectiveOreMode(OreFinding configured, boolean diamondPickaxeGoal, boolean diamondOre, boolean scanFirst) {
      return scanFirst && diamondPickaxeGoal && diamondOre ? OreFinding.Auto : configured;
   }

   private OreFinding currentOreMode(Ing ing) {
      OreFinding base = this.nativeOreScan.getAsBoolean() && this.oreMode != OreFinding.XRay ? OreFinding.Auto : this.oreMode;
      return effectiveOreMode(base, this.goals.containsKey(class_1802.field_8377),
         ing.canonical() == class_1802.field_8477, this.diamondScanFirst.getAsBoolean());
   }

   void setLocalStorage(BooleanSupplier enabled) { this.localStorage = enabled; }

   void setRoomMaker(BooleanSupplier maker) {
      this.roomMaker = maker;
   }

   String status() {
      return switch (this.phase) {
         case MINE -> "Mining " + (this.miningFor == null ? "" : this.miningFor.name()) + " (" + this.mineLastCount + "/" + this.mineTarget + ")";
         case PLACE -> "Placing a " + (this.placeItem == null ? "station" : name(this.placeItem));
         case OPEN -> this.collecting
            ? "Opening the furnace to collect"
            : "Opening the " + (this.targetBlock == class_2246.field_10181 ? "furnace" : "crafting table");
         case CLICK -> this.lastAction == null ? "Using a menu" : Character.toUpperCase(this.lastAction.charAt(0)) + this.lastAction.substring(1);
         case SMELTING -> this.smeltStatus();
         case WALK_BACK -> "Walking back to the furnace";
         case MINE_APPROACH -> "Descending beside the farm to the ore level";
         case BREAK -> "Picking up the " + (this.targetBlock == class_2246.field_10181 ? "furnace" : "crafting table");
         default -> "Working out the next step";
      };
   }

   String goalStatus() {
      if (MeteorClient.mc.field_1724 == null) {
         return null;
      } else {
         for (Entry<class_1792, Integer> g : this.goals.entrySet()) {
            class_1792 item = g.getKey();
            if (!this.skipped.contains(item) && !this.alreadyDone.test(item)) {
               int have = count(ex(item).match);
               if (have < g.getValue()) {
                  return name(item) + " " + have + "/" + g.getValue();
               }
            }
         }

         return null;
      }
   }

   boolean isOreMining() {
      return (this.phase == Phase.MINE || this.phase == Phase.MINE_APPROACH) && this.mining != null && this.mining.legitY() != null;
   }

   static baritone.api.pathing.goals.Goal approachGoal(boolean diamond, class_2338 entry) {
      return diamond ? new baritone.api.pathing.goals.GoalYLevel(entry.method_10264()) : new GoalNear(entry, 3);
   }

   static boolean needsMineApproach(boolean diamond, class_2338 player, class_2338 entry) {
      return diamond ? (long) player.method_10264() > (long) entry.method_10264() + 5
         : !player.method_19771(entry, 5.0);
   }

   boolean isWalking() {
      return this.phase == Crafter.Phase.MINE || this.phase == Crafter.Phase.WALK_BACK || this.phase == Crafter.Phase.MINE_APPROACH;
   }

   void interrupt() {
      boolean inMenu = this.phase == Crafter.Phase.OPEN || this.phase == Crafter.Phase.CLICK;
      this.clicks.clear();
      Look.closeScreen();
      if (inMenu) {
         if (this.stashing) {
            this.stashing = false;
            this.phase = Crafter.Phase.DECIDE;
         } else if (this.collecting) {
            this.phase = Crafter.Phase.SMELTING;
         } else if (this.pendingSmelt != null && this.target != null) {
            // A load was interrupted: whatever went in is collected later instead of being forgotten in the furnace.
            this.lanes.add(new Lane(this.target, this.ticks + 200, 0));
            this.pendingSmelt = null;
            this.phase = Crafter.Phase.SMELTING;
         } else {
            this.pendingCraft = null;
            this.phase = Crafter.Phase.DECIDE;
         }

         this.wait = 10;
      }
   }

   void reissue() {
      IBaritone b = baritone();
      if (this.phase == Phase.MINE && b != null && b.getMineProcess().isActive()) {
         this.miningProfile.run();
         FarmLogic.configureMiningSettings(BaritoneAPI.getSettings(), this.mineCeiling.getAsInt());
         BaritoneAPI.getSettings().legitMine.value = this.mineLegit;
         this.miningWatch.restart(this.ticks, MeteorClient.mc.field_1724.method_24515(), count(this.miningFor.match));
         return;
      }
      if (b != null) {
         b.getPathingBehavior().cancelEverything();
      }

      if ((this.phase == Crafter.Phase.MINE || this.phase == Crafter.Phase.MINE_APPROACH) && this.mining != null) {
         this.startMine(this.mining, this.miningFor, this.mineTarget);
      } else if (this.phase == Crafter.Phase.WALK_BACK) {
         this.phase = Crafter.Phase.SMELTING;
      } else if (this.phase != Crafter.Phase.CLICK && this.phase != Crafter.Phase.SMELTING) {
         Look.stopMining();
         this.clicks.clear();
         Look.closeScreen();
         this.phase = Crafter.Phase.DECIDE;
      }
   }

   boolean isClicking() {
      return !this.clicks.isEmpty() || this.phase == Crafter.Phase.CLICK || this.phase == Crafter.Phase.OPEN;
   }

   Crafter.Status tick() {
      this.ticks++;
      if (this.failReason != null) {
         return Crafter.Status.FAILED;
      } else if (this.wait > 0) {
         this.wait--;
         return Crafter.Status.RUNNING;
      } else {
         switch (this.phase) {
            case DECIDE:
               return this.decide();
            case MINE:
               this.tickMine();
               break;
            case MINE_APPROACH:
               this.tickMineApproach();
               break;
            case PLACE:
               this.tickPlace();
               break;
            case OPEN:
               this.tickOpen();
               break;
            case CLICK:
               this.tickClick();
               break;
            case SMELTING:
               this.tickSmelting();
               break;
            case WALK_BACK:
               this.tickWalkBack();
               break;
            case BREAK:
               this.tickBreak();
               break;
            case WAIT:
               this.phase = Crafter.Phase.DECIDE;
         }

         return this.failReason != null ? Crafter.Status.FAILED : Crafter.Status.RUNNING;
      }
   }

   private Crafter.Status decide() {
      if (this.emptySlots() <= 3 && this.roomMaker != null && !this.roomMaker.getAsBoolean()) return Crafter.Status.RUNNING;
      if (MeteorClient.mc.field_1724.method_31548().method_7376() == -1) {
         if (this.localStorage.getAsBoolean() && this.tryStash()) {
            return Crafter.Status.RUNNING;
         }

         if (this.roomMaker != null && !this.roomMaker.getAsBoolean()) {
            return Crafter.Status.RUNNING;
         }

         if (MeteorClient.mc.field_1724.method_31548().method_7376() == -1) {
            this.failReason = "Inventory is full of things I still need. Empty it and press the button again.";
            return Crafter.Status.FAILED;
         }
      }

      this.lanes.removeIf(l -> furnaceGone(l.pos));
      if (this.lanes.isEmpty()) {
         this.smeltingFor = null;
         this.lanePlanCount = 0;
      }
      if (this.readyLane() != null) {
         this.phase = Crafter.Phase.SMELTING;
         return Crafter.Status.RUNNING;
      } else {
         if (this.localStorage.getAsBoolean() && this.emptySlots() <= 3 && !this.stashNear() && count(st -> st.method_7909() == class_1802.field_8106) == 0 && this.starterDone) {
            try {
               if (!this.need(ex(class_1802.field_8106), 1, 0)) {
                  return Crafter.Status.RUNNING;
               }
            } catch (Crafter.Unobtainable var7) {
            }
         }

         if (!this.starterDone) {
            try {
               if (count(PICK_STONE.match) == 0 && count(LOG.match) < 4 && !this.need(LOG, 6, 0)) {
                  return Crafter.Status.RUNNING;
               }

               if (!this.need(PICK_STONE, 1, 0)) {
                  return Crafter.Status.RUNNING;
               }

               this.starterDone = true;
            } catch (Crafter.Unobtainable var6) {
               this.failReason = "Can't get started: no way to get " + var6.what + ".";
               return Crafter.Status.FAILED;
            }
         }

         for (Entry<class_1792, Integer> g : this.goals.entrySet()) {
            class_1792 item = g.getKey();
            if (!this.skipped.contains(item) && !this.alreadyDone.test(item)) {
               try {
                  if (!this.need(ex(item), g.getValue(), 0)) {
                     return Crafter.Status.RUNNING;
                  }
               } catch (Crafter.Unobtainable var5) {
                  this.skipped.add(item);
                  ChatUtils.warningPrefix("Farm Builder", "Can't make %s (no way to get %s), skipping it.", name(item), var5.what);
               }
            }
         }

         // Never report done while furnaces still hold items: wait and collect them first.
         if (!this.lanes.isEmpty()) {
            this.phase = Crafter.Phase.SMELTING;
            return Crafter.Status.RUNNING;
         }

         this.breakNearbyStations();
         return this.phase == Crafter.Phase.BREAK ? Crafter.Status.RUNNING : Crafter.Status.DONE;
      }
   }

   private boolean need(Crafter.Ing ing, int target, int depth) {
      int have = count(ing.match);
      if (have >= target) {
         return true;
      } else if (depth > 14) {
         throw new Crafter.Unobtainable(ing.name);
      } else {
         Crafter.Source src = SOURCES.get(ing);
         if (src == null) {
            throw new Crafter.Unobtainable(ing.name);
         } else {
            switch (src) {
               case Crafter.Craft c:
                  // Gather for every craft still missing (up to a stack of output), not just the next one,
                  // so 12 hoppers cost one iron trip instead of twelve.
                  int crafts = CraftMath.batchCrafts(target - have, c.out());
                  for (Entry<Character, Crafter.Ing> k : c.key().entrySet()) {
                     int per = c.amount(k.getKey());
                     if (!this.need(k.getValue(), CraftMath.ingredientTarget(per, crafts), depth + 1)) {
                        return false;
                     }
                  }

                  if (!c.small() && !this.station(class_2246.field_9980, class_1802.field_8465, depth)) {
                     return false;
                  }

                  if (!("craft " + ing.name).equals(this.lastAction)) {
                     ChatUtils.infoPrefix("Farm Builder", "Crafting %s.", ing.name);
                  }

                  this.track("craft " + ing.name, have);
                  this.pendingCraft = c;
                  this.pendingCrafts = crafts;
                  this.pendingSmelt = null;
                  IBaritone stopB = baritone();
                  if (stopB != null) {
                     stopB.getPathingBehavior().cancelEverything();
                  }

                  if (c.small()) {
                     Look.openInventoryIfClosed();
                     this.clickHandler = MeteorClient.mc.field_1724.field_7498;
                     this.queueCraft(c, false);
                     this.phase = Crafter.Phase.CLICK;
                  } else {
                     this.beginOpen(class_2246.field_9980);
                  }
                  break;
               case Crafter.Smelt s:
                  // Furnaces busy with a different item: wait for them instead of mixing batches.
                  if (!this.lanes.isEmpty() && this.smeltingFor != ing) {
                     this.phase = Crafter.Phase.SMELTING;
                     return false;
                  }

                  int cooking = this.laneItems();
                  int missing = target - have - cooking;
                  if (missing <= 0) {
                     this.phase = Crafter.Phase.SMELTING;
                     return false;
                  }

                  int n = Math.min(CraftMath.SMELT_BATCH, missing);
                  if (this.lanes.isEmpty()) {
                     if (!this.need(s.input(), n, depth + 1)) {
                        return false;
                     }

                     this.planLanes(n);
                     this.smeltingFor = ing;
                  } else {
                     // Furnaces are already cooking: only load more of them from what is carried,
                     // never walk off to gather while items sit in the furnaces.
                     n = Math.min(n, count(s.input().match()));
                     if (this.lanes.size() >= this.lanePlanCount || n <= 0) {
                        this.phase = Crafter.Phase.SMELTING;
                        return false;
                     }
                  }

                  int load = Math.max(1, Math.min(n, this.lanePlanSize));
                  boolean coal = count(Crafter::isCoal) * 8 >= load;
                  if (!coal && !this.need(PLANKS, (int)Math.ceil(load / 1.5), depth + 1)) {
                     return false;
                  }

                  if (!this.freeFurnace(depth)) {
                     return false;
                  }

                  if (!("smelt " + ing.name).equals(this.lastAction)) {
                     ChatUtils.infoPrefix("Farm Builder", this.lanePlanCount > 1 ? "Smelting %s in %d furnaces." : "Smelting %s.", ing.name, this.lanePlanCount);
                  }

                  this.track("smelt " + ing.name, have + cooking);
                  this.pendingSmelt = s;
                  this.pendingCraft = null;
                  this.smeltAmount = load;
                  this.smeltWithCoal = coal;
                  this.beginOpen(class_2246.field_10181);
                  break;
               case Crafter.Mine m:
                  if (!this.lanes.isEmpty()) {
                     this.phase = Crafter.Phase.SMELTING;
                     return false;
                  }

                  Crafter.Ing tool = switch (m.tier()) {
                     case 0 -> PICK_ANY;
                     case 1 -> PICK_STONE;
                     case 2 -> PICK_IRON;
                     default -> null;
                  };
                  if (tool != null && !this.need(tool, 1, depth + 1)) {
                     return false;
                  }

                  if (this.breakNearbyStations()) {
                     return false;
                  }

                  this.startMine(m, ing, target);
                  break;
               default:
                  throw new MatchException(null, null);
            }

            return false;
         }
      }
   }

   private void track(String action, int have) {
      if (action.equals(this.lastAction) && have <= this.lastActionHave) {
         if (++this.sameActionCount >= 4) {
            throw new Crafter.Unobtainable(action.substring(action.indexOf(32) + 1) + " (crafting keeps failing)");
         }
      } else {
         this.sameActionCount = 0;
      }

      this.lastAction = action;
      this.lastActionHave = have;
   }

   /** Only a loaded chunk can prove a furnace is gone; an unloaded one just reads as air while the bot is away. */
   private static boolean furnaceGone(class_2338 p) {
      return p == null || MeteorClient.mc.field_1687.method_8393(p.method_10263() >> 4, p.method_10260() >> 4) && !isFurnace(p);
   }

   private static boolean isFurnace(class_2338 p) {
      return p != null && MeteorClient.mc.field_1687.method_8320(p).method_27852(class_2246.field_10181);
   }

   private boolean laneAt(class_2338 p) {
      for (Lane l : this.lanes) if (l.pos.equals(p)) return true;
      return false;
   }

   private int laneItems() {
      int n = 0;
      for (Lane l : this.lanes) n += l.items;
      return n;
   }

   /** The finished lane to collect next, nearest first; null while everything is still cooking. */
   private Lane readyLane() {
      Lane best = null;
      for (Lane l : this.lanes) {
         if (l.readyAt > this.ticks) continue;
         if (best == null || this.inReach(l.pos) && !this.inReach(best.pos)) best = l;
      }
      return best;
   }

   private String smeltStatus() {
      if (this.lanes.isEmpty()) return "Smelting";
      int last = 0;
      for (Lane l : this.lanes) last = Math.max(last, l.readyAt);
      int secs = Math.max(0, (last - this.ticks) / 20);
      return this.lanes.size() == 1 ? "Smelting, " + secs + "s left" : "Smelting in " + this.lanes.size() + " furnaces, " + secs + "s left";
   }

   /**
    * Picks how many furnaces make this batch finish soonest. Each furnace smelts one item per 10 s, so
    * splitting n items over k furnaces takes ceil(n/k)*10 s, plus a few seconds per furnace to place,
    * craft (from carried cobblestone) and load. Only furnaces already in reach, carried, or craftable
    * from carried cobblestone are counted, so planning never sends the bot off to mine.
    */
   private void planLanes(int n) {
      int idle = 0;
      for (class_2338 p : this.stations) if (isFurnace(p) && this.inReach(p) && !this.laneAt(p)) idle++;
      int carried = count(st -> st.method_7909() == class_1802.field_8732);
      int craftable = count(ex(class_1802.field_20412).match()) / 8;
      int usable = Math.max(1, Math.min(4, idle + carried + craftable));
      int best = 1;
      double bestSeconds = Double.MAX_VALUE;
      for (int k = 1; k <= usable; k++) {
         int place = Math.max(0, k - idle);
         int craft = Math.max(0, place - carried);
         double seconds = Math.ceil(n / (double) k) * 10.0 + place * 4.0 + craft * 6.0 + k * 3.0;
         if (seconds + 0.5 < bestSeconds) {
            bestSeconds = seconds;
            best = k;
         }
      }
      this.lanePlanCount = best;
      this.lanePlanSize = (int) Math.ceil(n / (double) best);
   }

   /** Like station(), but the furnace must not already be cooking a lane. */
   private boolean freeFurnace(int depth) {
      this.stations.removeIf(px -> !MeteorClient.mc.field_1687.method_8320(px).method_27852(class_2246.field_9980) && !isFurnace(px));
      for (class_2338 p : this.stations) {
         if (isFurnace(p) && this.inReach(p) && !this.laneAt(p)) return true;
      }
      if (!this.need(ex(class_1802.field_8732), 1, depth + 1)) return false;
      this.placeItem = class_1802.field_8732;
      this.targetBlock = class_2246.field_10181;
      this.target = null;
      this.phaseStart = this.ticks;
      this.phase = Crafter.Phase.PLACE;
      return false;
   }

   private boolean station(class_2248 block, class_1792 item, int depth) {
      this.stations
         .removeIf(
            px -> !MeteorClient.mc.field_1687.method_8320(px).method_27852(class_2246.field_9980)
               && !MeteorClient.mc.field_1687.method_8320(px).method_27852(class_2246.field_10181)
         );

      for (class_2338 p : this.stations) {
         if (MeteorClient.mc.field_1687.method_8320(p).method_27852(block) && this.inReach(p)) {
            return true;
         }
      }

      if (!this.need(ex(item), 1, depth + 1)) {
         return false;
      } else {
         this.placeItem = item;
         this.targetBlock = block;
         this.target = null;
         this.phaseStart = this.ticks;
         this.phase = Crafter.Phase.PLACE;
         return false;
      }
   }

   private void tickPlace() {
      IBaritone b = baritone();
      if (this.target != null && MeteorClient.mc.field_1687.method_8320(this.target).method_27852(this.targetBlock)) {
         if (this.targetBlock == class_2246.field_10034) {
            this.stash = this.target;
            ChatUtils.infoPrefix(
               "Farm Builder", "Placed a storage chest at %d %d %d.", this.target.method_10263(), this.target.method_10264(), this.target.method_10260()
            );
         } else {
            this.stations.add(this.target);
         }

         this.placement = null;
         this.badSpots.clear();
         this.phase = Crafter.Phase.DECIDE;
      } else if (this.ticks >= this.asideUntil || b == null || !b.getCustomGoalProcess().isActive()) {
         this.asideUntil = 0;
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         if (this.ticks - this.phaseStart > 1200) {
            this.failReason = "Couldn't place a " + name(this.placeItem) + " anywhere for a minute. Move somewhere more open.";
         } else {
            int held = this.holdItem(st -> st.method_7909() == this.placeItem, 7);
            if (held < 0) {
               this.phase = Crafter.Phase.DECIDE;
            } else if (held != 0) {
               boolean spotOk = this.target != null
                  && this.placement != null
                  && MeteorClient.mc.field_1687.method_8320(this.target).method_45474()
                  && !this.blockedByEntity(this.target);
               if (spotOk && this.ticks - this.spotSince > 160) {
                  this.badSpots.add(this.target);
                  spotOk = false;
               }

               if (!spotOk) {
                  this.target = this.findSpot();
                  this.spotSince = this.ticks;
                  if (this.target == null) {
                     this.stepAside(b);
                     return;
                  }
               }

               Look.lookAt(this.placement.hit(), 10.0F + (float)Math.random() * 5.0F);
               if (Look.crosshairOn(this.placement.against(), this.placement.side())) {
                  Look.useCrosshairBlock();
                  this.wait = 5 + (int)(Math.random() * 4.0);
               }
            }
         }
      }
   }

   private void stepAside(IBaritone b) {
      this.badSpots.clear();
      this.target = null;
      if (b != null) {
         class_2338 feet = MeteorClient.mc.field_1724.method_24515();
         List<class_2338> options = new ArrayList<>();
         for (int radius = 1; radius <= 4 && options.isEmpty(); radius++) {
            for (int[] direction : new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{-1,1},{1,-1},{-1,-1}}) {
               class_2338 pos = feet.method_10069(direction[0] * radius, 0, direction[1] * radius);
               if (this.noPlace.test(pos) || !MeteorClient.mc.field_1687.method_8393(pos.method_10263() >> 4, pos.method_10260() >> 4)
                  || !MeteorClient.mc.field_1687.method_8311(pos) || !MeteorClient.mc.field_1687.method_8311(pos.method_10084())
                  || !MeteorClient.mc.field_1687.method_8316(pos.method_10074()).method_15769()
                  || MeteorClient.mc.field_1687.method_8320(pos.method_10074()).method_26220(MeteorClient.mc.field_1687, pos.method_10074()).method_1110()
                  || this.blockedByEntity(pos)) continue;
               options.add(pos);
            }
         }
         class_2338 side = Movement.bestRecoveryStep(feet, new GoalBlock(feet), options);
         if (side != null) {
            b.getCustomGoalProcess().setGoalAndPath(new GoalBlock(side));
            this.asideUntil = this.ticks + 80;
         }
      }
   }

   private boolean blockedByEntity(class_2338 p) {
      class_238 box = new class_238(p);
      return MeteorClient.mc.field_1724.method_5829().method_1009(0.05, 0.0, 0.05).method_994(box)
         ? true
         : !MeteorClient.mc.field_1687.method_8333(MeteorClient.mc.field_1724, box, e -> !(e instanceof class_1542) && !(e instanceof class_1303)).isEmpty();
   }

   private boolean tryStash() {
      if (this.stashFails < 3 && this.firstStashable() >= 0) {
         if (this.stash != null && !MeteorClient.mc.field_1687.method_8320(this.stash).method_27852(class_2246.field_10034)) {
            this.stash = null;
         }

         if (this.stash != null && !this.inReach(this.stash)) {
            if (this.stashNear()) {
               return false;
            }

            this.stash = null;
         }

         if (this.stash != null) {
            this.stashing = true;
            this.beginOpenAt(this.stash, class_2246.field_10034);
            return true;
         } else if (count(st -> st.method_7909() == class_1802.field_8106) == 0) {
            return false;
         } else {
            this.placeItem = class_1802.field_8106;
            this.targetBlock = class_2246.field_10034;
            this.target = null;
            this.placement = null;
            this.phaseStart = this.ticks;
            this.phase = Crafter.Phase.PLACE;
            return true;
         }
      } else {
         return false;
      }
   }

   private boolean stashNear() {
      return this.stash != null
         && MeteorClient.mc.field_1687.method_8320(this.stash).method_27852(class_2246.field_10034)
         && MeteorClient.mc.field_1724.method_24515().method_10262(this.stash) < 576.0;
   }

   private void queueStash() {
      this.clicks.clear();
      this.clickAbort = false;

      for (class_1735 sl : this.clickHandler.field_7761) {
         if (sl.field_7871 instanceof class_1661 && sl.method_34266() < 36) {
            int id = sl.field_7874;
            this.clicks.add(() -> {
               class_1799 st = this.clickHandler.method_7611(id).method_7677();
               if (!st.method_7960() && !this.keepForStash(st)) {
                  this.click(id, 0, class_1713.field_7794);
               }
            });
         }
      }
   }

   private int firstStashable() {
      for (int i = 0; i < 36; i++) {
         class_1799 st = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (!st.method_7960() && !this.keepForStash(st)) {
            return i;
         }
      }

      return -1;
   }

   private int emptySlots() {
      int n = 0;

      for (int i = 0; i < 36; i++) {
         if (MeteorClient.mc.field_1724.method_31548().method_5438(i).method_7960()) {
            n++;
         }
      }

      return n;
   }

   private boolean keepForStash(class_1799 st) {
      class_1792 item = st.method_7909();
      if (!BULK.contains(item)) {
         if (this.keep.test(item) || st.method_7963() || st.method_58694(class_9334.field_50075) != null) {
            return true;
         } else {
            return item != class_1802.field_8106 && item != class_1802.field_8465 && item != class_1802.field_8732 && !Boat.BOATS.contains(item)
               ? USEFUL.computeIfAbsent(item, Crafter::usedByRecipes)
               : true;
         }
      } else {
         return count(s -> s.method_7909() == item) <= 64 && this.keep.test(item);
      }
   }

   private static boolean usedByRecipes(class_1792 item) {
      class_1799 probe = new class_1799(item);

      for (Entry<Crafter.Ing, Crafter.Source> e : SOURCES.entrySet()) {
         if (e.getKey().match().test(probe)) {
            return true;
         }

         if (e.getValue() instanceof Crafter.Craft c && c.key().values().stream().anyMatch(i -> i.match().test(probe))) {
            return true;
         }

         if (e.getValue() instanceof Crafter.Smelt sm && sm.input().match().test(probe)) {
            return true;
         }
      }

      return false;
   }

   private int holdItem(Predicate<class_1799> match, int hotbarSlot) {
      if (Look.selectHotbar(match)) {
         if (MeteorClient.mc.field_1755 instanceof class_490) {
            Look.closeScreen();
         }

         return 1;
      } else {
         class_1661 inv = MeteorClient.mc.field_1724.method_31548();

         for (int i = 9; i < 36; i++) {
            if (match.test(inv.method_5438(i))) {
               if (Look.openInventoryIfClosed()) {
                  Look.clickSwapToHotbar(i, hotbarSlot);
               }

               this.wait = 3 + (int)(Math.random() * 3.0);
               return 0;
            }
         }

         return -1;
      }
   }

   private class_2338 findSpot() {
      class_2338 feet = MeteorClient.mc.field_1724.method_24515();
      class_2338 best = null;
      Look.Placement bestPl = null;
      double bestScore = Double.MAX_VALUE;

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               if (dx != 0 || dz != 0) {
                  class_2338 p = feet.method_10069(dx, dy, dz);
                  if (!this.badSpots.contains(p)
                     && !this.noPlace.test(p)
                     && this.inReach(p)
                     && MeteorClient.mc.field_1687.method_8320(p).method_45474()
                     && !MeteorClient.mc.field_1687.method_8320(p.method_10074()).method_26215()
                     && !this.blockedByEntity(p)) {
                     Look.Placement pl = Look.findPlacement(p);
                     if (pl != null) {
                        double score = MeteorClient.mc.field_1724.method_33571().method_1025(class_243.method_24953(p)) + Math.abs(dy) * 2;
                        if (score < bestScore) {
                           bestScore = score;
                           best = p;
                           bestPl = pl;
                        }
                     }
                  }
               }
            }
         }
      }

      this.placement = bestPl;
      return best;
   }

   private void beginOpen(class_2248 block) {
      this.target = null;

      for (class_2338 p : this.stations) {
         if (MeteorClient.mc.field_1687.method_8320(p).method_27852(block) && this.inReach(p) && !this.laneAt(p)) {
            this.target = p;
         }
      }

      if (this.target == null) {
         this.phase = Crafter.Phase.DECIDE;
      } else {
         this.targetBlock = block;
         this.phaseStart = this.ticks;
         this.useSent = false;
         this.phase = Crafter.Phase.OPEN;
      }
   }

   private void beginOpenAt(class_2338 pos, class_2248 block) {
      this.target = pos;
      this.targetBlock = block;
      this.phaseStart = this.ticks;
      this.useSent = false;
      this.phase = Crafter.Phase.OPEN;
   }

   private void tickOpen() {
      class_1703 h = MeteorClient.mc.field_1724.field_7512;
      boolean ready = this.targetBlock == class_2246.field_9980
         ? h instanceof class_1714
         : (this.targetBlock == class_2246.field_10034 ? h instanceof class_1707 : h instanceof class_1720);
      if (ready) {
         this.clickHandler = h;
         if (this.stashing) {
            this.queueStash();
         } else if (this.collecting) {
            this.clicks.clear();
            this.clicks.add(() -> this.click(2, 0, class_1713.field_7794));
            this.clicks.add(() -> this.click(0, 0, class_1713.field_7794));
            this.clicks.add(() -> this.click(1, 0, class_1713.field_7794));
         } else if (this.pendingCraft != null) {
            this.queueCraft(this.pendingCraft, true);
         } else {
            this.queueSmelt();
         }

         this.phase = Crafter.Phase.CLICK;
         this.wait = 4;
      } else if (this.ticks - this.phaseStart > 120) {
         this.stations.remove(this.target);
         if (this.collecting) {
            // A furnace that will not open (claimed land, lag) must not be retried forever.
            if (this.collectingLane != null) this.lanes.remove(this.collectingLane);
            this.collecting = false;
            this.collectingLane = null;
         }
         if (this.stashing) {
            this.stashing = false;
            this.stash = null;
            this.stashFails++;
         }

         this.phase = Crafter.Phase.DECIDE;
      } else {
         if (this.useSent && this.ticks - this.useSentAt > 25) {
            this.useSent = false;
         }

         if (!this.useSent) {
            Look.lookAt(class_243.method_24953(this.target), 10.0F + (float)Math.random() * 5.0F);
            if (Look.crosshairOn(this.target)) {
               Look.expectScreen();
               Look.useCrosshairBlock();
               this.useSent = true;
               this.useSentAt = this.ticks;
            }
         }
      }
   }

   private boolean breakNearbyStations() {
      this.stations.removeIf(p -> !this.inReach(p) || MeteorClient.mc.field_1687.method_8320(p).method_26215());
      List<class_2338> breakable = new ArrayList<>(this.stations);
      breakable.removeIf(this::laneAt);
      if (breakable.isEmpty()) {
         return false;
      } else {
         Look.closeScreen();
         this.target = breakable.get(0);
         this.targetBlock = MeteorClient.mc.field_1687.method_8320(this.target).method_26204();
         this.phaseStart = this.ticks;
         this.phase = Crafter.Phase.BREAK;
         return true;
      }
   }

   private void tickBreak() {
      if (!MeteorClient.mc.field_1687.method_8320(this.target).method_26215() && this.ticks - this.phaseStart <= 300) {
         Set<class_1792> tools = this.targetBlock == class_2246.field_10181
            ? Set.of(class_1802.field_22024, class_1802.field_8377, class_1802.field_8403, class_1802.field_8387, class_1802.field_8335, class_1802.field_8647)
            : Set.of(class_1802.field_22025, class_1802.field_8556, class_1802.field_8475, class_1802.field_8062, class_1802.field_8406);
         if (this.holdItem(st -> tools.contains(st.method_7909()), 6) != 0) {
            Look.lookAt(class_243.method_24953(this.target), 10.0F + (float)Math.random() * 5.0F);
            if (Look.crosshairOn(this.target)) {
               Look.attackCrosshairBlock();
            }
         }
      } else {
         Look.stopMining();
         this.stations.remove(this.target);
         this.wait = 15;
         this.phase = Crafter.Phase.DECIDE;
      }
   }

   private boolean inReach(class_2338 p) {
      return MeteorClient.mc.field_1724.method_33571().method_1022(class_243.method_24953(p)) <= 4.0;
   }

   private void queueCraft(Crafter.Craft c, boolean table) {
      this.clicks.clear();
      this.clickAbort = false;
      int width = table ? 3 : 2;
      List<Integer> used = new ArrayList<>();
      int batch = this.craftsThatFit(c);

      for (int row = 0; row < c.rows().length; row++) {
         String line = c.rows()[row];

         for (int col = 0; col < line.length(); col++) {
            char ch = line.charAt(col);
            if (ch != ' ') {
               Crafter.Ing ing = c.key().get(ch);
               int grid = 1 + row * width + col;
               used.add(grid);
               this.clicks.add(() -> {
                  if (!this.clickAbort) {
                     int src = this.findSource(ing.match);
                     if (src < 0) {
                        this.clickAbort = true;
                     } else {
                        this.lastSrc = src;
                        this.click(src, 0, class_1713.field_7790);
                     }
                  }
               });
               for (int copy = 0; copy < batch; copy++) {
                  this.clicks.add(() -> {
                     if (!this.clickAbort && !this.clickHandler.method_34255().method_7960()) {
                        this.click(grid, 1, class_1713.field_7790);
                     }
                  });
               }
               this.clicks.add(() -> {
                  if (!this.clickAbort) {
                     this.click(this.lastSrc, 0, class_1713.field_7790);
                  }
               });
            }
         }
      }

      this.clicks.add(() -> {
         if (!this.clickAbort) {
            this.click(0, 0, class_1713.field_7794);
         }
      });
      this.clicks.add(() -> {
         for (int g : used) {
            if (!this.clickHandler.method_7611(g).method_7677().method_7960()) {
               this.click(g, 0, class_1713.field_7794);
            }
         }
      });
   }

   private int craftsThatFit(Crafter.Craft c) {
      List<Integer> sizes = new ArrayList<>();
      List<Integer> per = new ArrayList<>();
      for (Entry<Character, Crafter.Ing> k : c.key().entrySet()) {
         int src = this.findSource(k.getValue().match);
         if (src < 0) continue;
         sizes.add(this.clickHandler.method_7611(src).method_7677().method_7947());
         per.add(c.amount(k.getKey()));
      }
      return CraftMath.feasibleCrafts(this.pendingCrafts, sizes.stream().mapToInt(Integer::intValue).toArray(), per.stream().mapToInt(Integer::intValue).toArray());
   }

   private void queueSmelt() {
      this.clicks.clear();
      this.clickAbort = false;
      Crafter.Ing input = this.pendingSmelt.input();
      Predicate<class_1799> fuelMatch = this.smeltWithCoal ? Crafter::isCoal : PLANKS.match;
      int fuel = (int)Math.ceil(this.smeltAmount / (this.smeltWithCoal ? 8.0 : 1.5));
      this.clicks.add(this.fill(0, input.match, this.smeltAmount));
      this.clicks.add(this::putBack);
      this.clicks.add(this.fill(1, fuelMatch, fuel));
      this.clicks.add(this::putBack);
   }

   /**
    * Fills one furnace slot to exactly {@code want} items: whole stacks while they fit, then one at a time,
    * picking up further stacks when the cursor runs dry. Re-queues itself until done, one click per step.
    */
   private Runnable fill(int slot, Predicate<class_1799> match, int want) {
      return new Runnable() {
         // Each step either picks up or places items; if the server keeps rejecting clicks, stop instead of looping.
         private int steps = 2 * want + 8;

         @Override
         public void run() {
            if (Crafter.this.clickAbort || --this.steps < 0) return;
            class_1799 inSlot = Crafter.this.clickHandler.method_7611(slot).method_7677();
            if (!inSlot.method_7960() && !match.test(inSlot)) {
               Crafter.this.clickAbort = true;
               return;
            }
            int have = inSlot.method_7960() ? 0 : inSlot.method_7947();
            if (have >= want) return;
            class_1799 cursor = Crafter.this.clickHandler.method_34255();
            if (cursor.method_7960()) {
               int src = Crafter.this.findSource(match);
               if (src < 0) return;
               Crafter.this.lastSrc = src;
               Crafter.this.click(src, 0, class_1713.field_7790);
            } else if (!match.test(cursor)) {
               Crafter.this.clickAbort = true;
               return;
            } else if (have + cursor.method_7947() <= want) {
               Crafter.this.click(slot, 0, class_1713.field_7790);
            } else {
               Crafter.this.click(slot, 1, class_1713.field_7790);
            }
            Crafter.this.clicks.addFirst(this);
         }
      };
   }

   private void putBack() {
      if (!this.clickHandler.method_34255().method_7960() && this.lastSrc >= 0) {
         this.click(this.lastSrc, 0, class_1713.field_7790);
      }
   }

   private int findSource(Predicate<class_1799> match) {
      for (class_1735 s : this.clickHandler.field_7761) {
         if (s.field_7871 instanceof class_1661 && s.method_34266() < 36) {
            class_1799 st = s.method_7677();
            if (!st.method_7960() && match.test(st)) {
               return s.field_7874;
            }
         }
      }

      return -1;
   }

   private void click(int slot, int button, class_1713 type) {
      MeteorClient.mc.field_1761.method_2906(this.clickHandler.field_7763, slot, button, type, MeteorClient.mc.field_1724);
   }

   private void tickClick() {
      if (!handlerMatches(this.clickHandler, MeteorClient.mc.field_1724.field_7512)) {
         this.clicks.clear();
         this.clickHandler = null;
         this.stashing = false;
         this.collecting = false;
         this.phase = Crafter.Phase.DECIDE;
      } else {
         Runnable next = this.clicks.poll();
         if (next != null) {
            next.run();
            this.wait = 2 + (int)(Math.random() * 3.0);
         } else {
            if (this.stashing) {
               this.stashing = false;
               if (MeteorClient.mc.field_1724.method_31548().method_7376() == -1) {
                  this.stash = null;
                  this.stashFails++;
               } else {
                  this.stashFails = 0;
               }
            }

            if (this.clickHandler instanceof class_1720) {
               class_1799 in = this.clickHandler.method_7611(0).method_7677();
               class_1799 fu = this.clickHandler.method_7611(1).method_7677();
               class_1799 out = this.clickHandler.method_7611(2).method_7677();
               if (this.collecting) {
                  this.collecting = false;
                  Lane done = this.collectingLane;
                  this.collectingLane = null;
                  if (done != null) {
                     this.lanes.remove(done);
                     // Inventory was too full to take everything: keep the furnace on the list and come back.
                     if (!in.method_7960() || !out.method_7960()) this.lanes.add(new Lane(done.pos, this.ticks + 100, in.method_7960() ? 0 : in.method_7947()));
                  }
               } else if (this.pendingSmelt != null) {
                  // Time the wait by what is really in the furnace, not by what was planned.
                  int items = in.method_7960() ? 0 : in.method_7947();
                  // +1: the piece already burning has left the fuel slot but still smelts its share.
                  int burnable = fu.method_7960() ? items : (int)Math.floor((fu.method_7947() + 1) * (isCoal(fu) ? 8.0 : 1.5));
                  int smelting = Math.min(items, burnable);
                  // Even an aborted load is tracked if anything went in, so it is collected instead of forgotten.
                  if (items > 0 && this.target != null && !this.laneAt(this.target)) {
                     this.lanes.add(new Lane(this.target, this.ticks + Math.max(1, smelting) * 200 + 30 + (int)(Math.random() * 40.0), smelting));
                  }
               }
            }

            if (MeteorClient.mc.field_1755 != null) {
               MeteorClient.mc.field_1724.method_7346();
            }

            this.pendingCraft = null;
            this.pendingSmelt = null;
            this.wait = 4;
            this.phase = Crafter.Phase.WAIT;
         }
      }
   }

   boolean isSmeltWaiting() {
      return this.phase == Crafter.Phase.SMELTING && !this.lanes.isEmpty() && this.readyLane() == null;
   }

   private void tickSmelting() {
      this.lanes.removeIf(l -> furnaceGone(l.pos));
      Lane ready = this.readyLane();
      if (this.lanes.isEmpty()) {
         this.collecting = false;
         this.collectingLane = null;
         this.phase = Crafter.Phase.DECIDE;
      } else if (ready == null) {
         if (this.ticks % 40 == 0 && Math.random() < 0.5) {
            Look.idleLook(new Random());
         }
      } else if (this.inReach(ready.pos)) {
         this.collecting = true;
         this.collectingLane = ready;
         this.beginOpenAt(ready.pos, class_2246.field_10181);
      } else {
         IBaritone b = baritone();
         if (b == null) {
            this.lanes.remove(ready);
            this.phase = Crafter.Phase.DECIDE;
         } else {
            this.walkBackTo = ready.pos;
            b.getCustomGoalProcess().setGoalAndPath(new GoalNear(ready.pos, 2));
            this.phaseStart = this.ticks;
            this.phase = Crafter.Phase.WALK_BACK;
         }
      }
   }

   private void tickWalkBack() {
      IBaritone b = baritone();
      class_2338 to = this.walkBackTo;
      if (to != null && this.inReach(to)) {
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         this.phase = Crafter.Phase.SMELTING;
      } else if (this.ticks - this.phaseStart <= 800 && to != null && !furnaceGone(to)) {
         if (b != null && !b.getCustomGoalProcess().isActive()) {
            b.getCustomGoalProcess().setGoalAndPath(new GoalNear(to, 2));
         }
      } else {
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         ChatUtils.warningPrefix("Farm Builder", "Couldn't get back to a furnace, smelting that part again elsewhere.");
         this.lanes.removeIf(l -> l.pos.equals(to));
         this.walkBackTo = null;
         this.phase = Crafter.Phase.DECIDE;
      }
   }

   private void startMine(Crafter.Mine m, Crafter.Ing ing, int target) {
      IBaritone b = baritone();
      if (b == null) {
         this.failReason = "Baritone isn't loaded.";
      } else {
         boolean continuing = (this.phase == Phase.MINE || this.phase == Phase.MINE_APPROACH)
            && m == this.mining && ing == this.miningFor && target == this.mineTarget;
         if (!continuing) {
            this.mineRestarts = 0;
            this.approachStarted = this.ticks;
            this.descent.start(this.ticks, MeteorClient.mc.field_1724.method_23318());
            this.miningWatch.start(this.ticks, MeteorClient.mc.field_1724.method_24515(), count(ing.match));
         }
         this.miningProfile.run();
         Settings s = BaritoneAPI.getSettings();
         this.mineRestartAt = 0;
         OreFinding mode = this.currentOreMode(ing);
         boolean previousLegit = this.mineLegit;
         this.mineLegit = m.legitY() != null
            && (mode == OreFinding.Legit || mode == OreFinding.Auto && this.xrayFailed.contains(ing));
         if (previousLegit != this.mineLegit) {
            this.approachStarted = this.ticks;
            this.descent.start(this.ticks, MeteorClient.mc.field_1724.method_23318());
            this.miningWatch.start(this.ticks, MeteorClient.mc.field_1724.method_24515(), count(ing.match));
         }
         FarmLogic.configureMiningSettings(s, this.mineCeiling.getAsInt());
         class_2338 anchor = this.mineAnchor.get();
         if (this.mineLegit && anchor != null && m.legitY() != null && MeteorClient.mc.field_1687.method_27983().method_29177().toString().equals("minecraft:overworld")) {
            int y = Math.max(MeteorClient.mc.field_1687.method_31607() + 5, Math.min(m.legitY(), this.mineCeiling.getAsInt()));
            class_2338 entry = new class_2338(anchor.method_10263(), y, anchor.method_10260());
            if (needsMineApproach(ing.canonical() == class_1802.field_8477, MeteorClient.mc.field_1724.method_24515(), entry)) {
               this.mining = m;
               this.miningFor = ing;
               this.mineTarget = target;
               this.mineApproach = entry;
               this.phaseStart = this.ticks;
               this.phase = Phase.MINE_APPROACH;
               b.getPathingBehavior().cancelEverything();
               s.legitMine.value = false;
               b.getCustomGoalProcess().setGoalAndPath(approachGoal(ing.canonical() == class_1802.field_8477, entry));
               ChatUtils.infoPrefix("Farm Builder", "Descending beside the farm to Y %d for %s.", y, ing.name());
               return;
            }
         }
         s.allowInventory.value = true;
         s.exploreForBlocks.value = true;
         s.antiCheatCompatibility.value = true;
         s.legitMine.value = this.mineLegit;
         s.maxYLevelWhileMining.value = m.tier() >= 0 ? this.mineCeiling.getAsInt() : 2031;
         if (m.legitY() != null) {
            s.legitMineYLevel.value = m.legitY();
         }

         this.mining = m;
         this.miningFor = ing;
         this.mineTarget = target;
         this.mineLastCount = count(ing.match);
         this.mineLastProgress = this.ticks;
         this.miningWatch.restart(this.ticks, MeteorClient.mc.field_1724.method_24515(), this.mineLastCount);
         this.phaseStart = this.ticks;
         b.getPathingBehavior().cancelEverything();
         b.getMineProcess().mine(0, m.blocks());
         ChatUtils.infoPrefix("Farm Builder", "Mining %s (%d/%d).", ing.name, this.mineLastCount, target);
         this.phase = Crafter.Phase.MINE;
      }
   }

   private void tickMineApproach() {
      IBaritone b = baritone();
      if (b == null) {
         this.failReason = "Baritone isn't loaded.";
         return;
      }
      this.descent.observe(this.ticks, MeteorClient.mc.field_1724.method_23318());
      boolean diamond = this.miningFor.canonical() == class_1802.field_8477;
      if (!needsMineApproach(diamond, MeteorClient.mc.field_1724.method_24515(), this.mineApproach)) {
         b.getPathingBehavior().cancelEverything();
         this.startMine(this.mining, this.miningFor, this.mineTarget);
      } else if (diamond ? this.descent.expired(this.ticks) : this.ticks - this.approachStarted > 2400) {
         b.getPathingBehavior().cancelEverything();
         this.throwSkip(this.miningFor);
      } else if (!b.getCustomGoalProcess().isActive() && this.ticks % 20 == 0) {
         b.getCustomGoalProcess().setGoalAndPath(approachGoal(diamond, this.mineApproach));
      }
   }

   boolean canContinueWithFullInventory() {
      if (this.phase != Phase.MINE || this.miningFor == null || MeteorClient.mc.field_1724 == null) return false;
      for (int i = 0; i < 36; i++) {
         class_1799 stack = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (this.miningFor.match.test(stack) && FarmLogic.hasStackRoom(stack)) return true;
      }
      return false;
   }

   String miningStatus() {
      return this.phase == Phase.MINE || this.phase == Phase.MINE_APPROACH
         ? (this.mineLegit ? "exploration: " : "known-ore scan: ") + (this.miningFor == null ? "ores" : this.miningFor.name())
            + "; restart " + this.mineRestarts + "/3" : "inactive";
   }

   private void tickMine() {
      IBaritone b = baritone();
      if (this.workBlock != null && MeteorClient.mc.field_1687.method_8320(this.workBlock).method_26215()) {
         this.miningWatch.blockCleared(this.ticks, this.workBlock);
         this.workBlock = null;
      }
      if (Look.isBreaking(b)
         && MeteorClient.mc.field_1765 instanceof net.minecraft.class_3965 hit
         && hit.method_17783() == net.minecraft.class_239.class_240.field_1332
         && !MeteorClient.mc.field_1687.method_8320(hit.method_17777()).method_26215()) this.workBlock = hit.method_17777().method_10062();
      Crafter.Ing tool = switch (this.mining.tier()) {
         case 0 -> PICK_ANY;
         case 1 -> PICK_STONE;
         case 2 -> PICK_IRON;
         default -> null;
      };
      if (tool != null && this.ticks % 20 == 0 && count(tool.match) == 0) {
         if (b != null) b.getPathingBehavior().cancelEverything();
         BaritoneAPI.getSettings().legitMine.value = false;
         this.phase = Phase.DECIDE;
         this.starterDone = false;
         this.wait = 5;
         ChatUtils.infoPrefix("Farm Builder", "Mining tool wore out; gathering materials for a replacement.");
         return;
      }
      if (this.mineRestartAt > this.ticks) return;
      if (this.mineRestartAt != 0) {
         this.mineRestartAt = 0;
         this.startMine(this.mining, this.miningFor, this.mineTarget);
         return;
      }
      int have = count(this.miningFor.match);
      if (this.miningWatch.observe(this.ticks, MeteorClient.mc.field_1724.method_24515(), have)) this.mineRestarts = 0;
      if (have > this.mineLastCount) {
         this.mineLastCount = have;
         this.mineLastProgress = this.ticks;
      }

      if (MeteorClient.mc.field_1724.method_31548().method_7376() == -1 && !this.canContinueWithFullInventory()) {
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         this.phase = Crafter.Phase.DECIDE;
         this.wait = 5;
      } else if (have >= this.mineTarget) {
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         BaritoneAPI.getSettings().legitMine.value = false;
         this.phase = Crafter.Phase.DECIDE;
         this.wait = 10;
      } else {
         int stall = this.mineStallSeconds * (this.mineLegit ? 3 : 1);
         boolean stopped = b == null || !b.getMineProcess().isActive();
         boolean noYield = this.miningWatch.noYield(this.ticks, stall);
         if (this.miningWatch.stalled(this.ticks, stall) || noYield || stopped && this.ticks - this.phaseStart > 60) {
            if (!noYield && b != null && b.getMineProcess().isActive() && ++this.mineRestarts <= 3) {
               b.getMineProcess().onTick(true, false);
               this.miningWatch.restart(this.ticks, MeteorClient.mc.field_1724.method_24515(), have);
               ChatUtils.infoPrefix("Farm Builder", "Blocked mining route; keeping unreachable-ore memory and choosing another target (%d/3).", this.mineRestarts);
               return;
            }
            if (b != null) {
               b.getPathingBehavior().cancelEverything();
            }

            BaritoneAPI.getSettings().legitMine.value = false;
            if (!noYield && ++this.mineRestarts <= 3) {
               this.mineRestartAt = this.ticks + 20 * this.mineRestarts;
               ChatUtils.infoPrefix("Farm Builder", "Mining paused before completion; rescanning and replanning (%d/3).", this.mineRestarts);
               return;
            }
            this.mineRestarts = 0;
            if (!this.mineLegit && this.mining.legitY() != null && this.currentOreMode(this.miningFor) == Crafter.OreFinding.Auto && this.xrayFailed.add(this.miningFor)) {
               ChatUtils.warningPrefix(
                  "Farm Builder", "Known-ore scan found no %s; exploring and strip mining at Y %d.", this.miningFor.name(), this.mining.legitY()
               );
               this.startMine(this.mining, this.miningFor, this.mineTarget);
               return;
            }

            this.throwSkip(this.miningFor);
         }
      }
   }

   private void throwSkip(Crafter.Ing ing) {
      if (!this.starterDone) {
         this.failReason = "Couldn't find any " + ing.name + " nearby. Go somewhere with trees and stone and press the button again.";
      } else {
         for (Entry<class_1792, Integer> g : this.goals.entrySet()) {
            class_1792 item = g.getKey();
            if (!this.skipped.contains(item) && !this.alreadyDone.test(item) && count(ex(item).match) < g.getValue()) {
               this.skipped.add(item);
               ChatUtils.warningPrefix("Farm Builder", "Couldn't find any %s nearby, skipping %s.", ing.name, name(item));
               break;
            }
         }

         this.phase = Crafter.Phase.DECIDE;
      }
   }

   static int count(Predicate<class_1799> match) {
      class_1661 inv = MeteorClient.mc.field_1724.method_31548();
      int n = 0;

      for (int i = 0; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (!st.method_7960() && match.test(st)) {
            n += st.method_7947();
         }
      }

      return n;
   }

   private static String name(class_1792 item) {
      return item.method_63680().getString();
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   static {
      mine(
         LOG,
         -1,
         null,
         class_2246.field_10431,
         class_2246.field_10037,
         class_2246.field_10511,
         class_2246.field_10306,
         class_2246.field_10533,
         class_2246.field_10010,
         class_2246.field_37545,
         class_2246.field_42729,
         class_2246.field_54715
      );
      mine(ex(class_1802.field_8583), -1, null, class_2246.field_10431);
      mine(ex(class_1802.field_20412), 0, null, class_2246.field_10340, class_2246.field_10445);
      mine(ex(class_1802.field_33400), 1, 16, class_2246.field_10212, class_2246.field_29027);
      mine(ex(class_1802.field_33402), 2, -16, class_2246.field_10571, class_2246.field_29026);
      mine(ex(class_1802.field_8725), 2, -58, class_2246.field_10080, class_2246.field_29030);
      mine(ex(class_1802.field_8477), 2, -54, class_2246.field_10442, class_2246.field_29029);
      mine(ex(class_1802.field_8858), -1, null, class_2246.field_10102);
      mine(ex(class_1802.field_8831), -1, null, class_2246.field_10566, class_2246.field_10219);
      smelt(class_1802.field_8620, class_1802.field_33400);
      smelt(class_1802.field_8695, class_1802.field_33402);
      smelt(class_1802.field_20391, class_1802.field_20412);
      smelt(class_1802.field_8280, class_1802.field_8858);
      craft(PLANKS, 4, r("L"), 'L', LOG);
      craft(class_1802.field_8118, 4, r("L"), 'L', class_1802.field_8583);
      craft(class_1802.field_8600, 4, r("P", "P"), 'P', PLANKS);
      craft(class_1802.field_8465, 1, r("PP", "PP"), 'P', PLANKS);
      craft(class_1802.field_8320, 6, r("PPP"), 'P', class_1802.field_8118);
      craft(class_1802.field_8106, 1, r("PPP", "P P", "PPP"), 'P', PLANKS);
      craft(class_1802.field_16307, 1, r("PSP", "P P", "PSP"), 'P', PLANKS, 'S', class_1802.field_8320);
      craft(class_1802.field_17530, 1, r("S S", "S S", "SSS"), 'S', class_1802.field_8320);
      craft(class_1802.field_8121, 3, r("S S", "SSS", "S S"), 'S', class_1802.field_8600);
      craft(class_1802.field_8874, 1, r("SPS", "SPS"), 'S', class_1802.field_8600, 'P', class_1802.field_8118);
      craft(class_1802.field_8788, 3, r("PPP", "PPP", " S "), 'P', class_1802.field_8118, 'S', class_1802.field_8600);
      craft(class_1802.field_8376, 2, r("PPP", "PPP"), 'P', class_1802.field_8118);
      craft(class_1802.field_8792, 3, r("PSP", "PSP"), 'P', class_1802.field_8118, 'S', class_1802.field_8600);
      craft(PICK_ANY, 1, r("PPP", " S ", " S "), 'P', PLANKS, 'S', class_1802.field_8600);
      craft(PICK_STONE, 1, r("CCC", " S ", " S "), 'C', class_1802.field_20412, 'S', class_1802.field_8600);
      craft(PICK_IRON, 1, r("III", " S ", " S "), 'I', class_1802.field_8620, 'S', class_1802.field_8600);
      craft(class_1802.field_8732, 1, r("CCC", "C C", "CCC"), 'C', class_1802.field_20412);
      craft(class_1802.field_8595, 6, r("SSS"), 'S', class_1802.field_20391);
      craft(class_1802.field_8865, 1, r("S", "C"), 'S', class_1802.field_8600, 'C', class_1802.field_20412);
      craft(class_1802.field_8530, 1, r("R", "S"), 'R', class_1802.field_8725, 'S', class_1802.field_8600);
      craft(class_1802.field_8619, 1, r("TRT", "SSS"), 'T', class_1802.field_8530, 'R', class_1802.field_8725, 'S', class_1802.field_20391);
      craft(class_1802.field_8878, 1, r("CCC", "C C", "CRC"), 'C', class_1802.field_20412, 'R', class_1802.field_8725);
      craft(class_1802.field_8249, 1, r("PPP", "CIC", "CRC"), 'P', PLANKS, 'C', class_1802.field_20412, 'I', class_1802.field_8620, 'R', class_1802.field_8725);
      craft(class_1802.field_8239, 1, r("I I", "ICI", " I "), 'I', class_1802.field_8620, 'C', class_1802.field_8106);
      craft(
         class_1802.field_46791,
         1,
         r("III", "ITI", "RDR"),
         'I',
         class_1802.field_8620,
         'T',
         class_1802.field_8465,
         'R',
         class_1802.field_8725,
         'D',
         class_1802.field_8878
      );
      craft(class_1802.field_8793, 1, r("RRR", "RRR", "RRR"), 'R', class_1802.field_8725);
      craft(class_1802.field_8129, 16, r("I I", "ISI", "I I"), 'I', class_1802.field_8620, 'S', class_1802.field_8600);
      craft(class_1802.field_8848, 6, r("G G", "GSG", "GRG"), 'G', class_1802.field_8695, 'S', class_1802.field_8600, 'R', class_1802.field_8725);
      craft(class_1802.field_8773, 1, r("III", "III", "III"), 'I', class_1802.field_8620);
      craft(class_1802.field_8638, 1, r("I I", "I I", "III"), 'I', class_1802.field_8620);
      craft(class_1802.field_8550, 1, r("I I", " I "), 'I', class_1802.field_8620);
      craft(class_1802.field_8603, 1, r("DDD", "DDD", "DDD"), 'D', class_1802.field_8477);
      craft(class_1802.field_8377, 1, r("DDD", " S ", " S "), 'D', class_1802.field_8477, 'S', class_1802.field_8600);
      craft(class_1802.field_8556, 1, r("DD", "DS", " S"), 'D', class_1802.field_8477, 'S', class_1802.field_8600);
      craft(class_1802.field_8250, 1, r("D", "S", "S"), 'D', class_1802.field_8477, 'S', class_1802.field_8600);
      craft(class_1802.field_8802, 1, r("D", "D", "S"), 'D', class_1802.field_8477, 'S', class_1802.field_8600);
      craft(class_1802.field_8805, 1, r("DDD", "D D"), 'D', class_1802.field_8477);
      craft(class_1802.field_8058, 1, r("D D", "DDD", "DDD"), 'D', class_1802.field_8477);
      craft(class_1802.field_8348, 1, r("DDD", "D D", "D D"), 'D', class_1802.field_8477);
      craft(class_1802.field_8285, 1, r("D D", "D D"), 'D', class_1802.field_8477);
      for (String material : List.of("wooden", "stone", "iron", "golden", "diamond")) {
         Ing ingredient = switch (material) {
            case "wooden" -> PLANKS;
            case "stone" -> ex(class_1802.field_20412);
            case "iron" -> ex(class_1802.field_8620);
            case "golden" -> ex(class_1802.field_8695);
            default -> ex(class_1802.field_8477);
         };
         for (String family : List.of("pickaxe", "axe", "shovel")) {
            class_1792 item = class_7923.field_41178.method_63535(net.minecraft.class_2960.method_60654("minecraft:" + material + "_" + family));
            if (item == null || item == class_1802.field_8162) continue;
            String[] rows = switch (family) {
               case "pickaxe" -> r("MMM", " S ", " S ");
               case "axe" -> r("MM", "MS", " S");
               default -> r("M", "S", "S");
            };
            craft(item, 1, rows, 'M', ingredient, 'S', class_1802.field_8600);
         }
      }
      SOURCES.put(ex(PICK_ANY.canonical()), SOURCES.get(PICK_ANY));
      SOURCES.put(ex(PICK_STONE.canonical()), SOURCES.get(PICK_STONE));
      SOURCES.put(ex(PICK_IRON.canonical()), SOURCES.get(PICK_IRON));
   }

   private record Craft(String[] rows, Map<Character, Crafter.Ing> key, int out) implements Crafter.Source {
      boolean small() {
         return this.rows.length <= 2 && Arrays.stream(this.rows).allMatch(r -> r.length() <= 2);
      }

      int amount(char c) {
         int n = 0;

         for (String r : this.rows) {
            for (char x : r.toCharArray()) {
               if (x == c) {
                  n++;
               }
            }
         }

         return n;
      }
   }

   private record Ing(String name, Predicate<class_1799> match, class_1792 canonical) {
   }

   private record Mine(class_2248[] blocks, int tier, Integer legitY) implements Crafter.Source {
   }

   static enum OreFinding {
      XRay("X-Ray (fastest)"),
      Auto("X-Ray, then legit if nothing found"),
      Legit("Legit strip mining only");

      private final String title;

      private OreFinding(String title) {
         this.title = title;
      }

      @Override
      public String toString() {
         return this.title;
      }
   }

   private static enum Phase {
      DECIDE,
      MINE,
      PLACE,
      OPEN,
      CLICK,
      SMELTING,
      WALK_BACK,
      BREAK,
      WAIT,
      MINE_APPROACH;
   }

   private record Smelt(Crafter.Ing input) implements Crafter.Source {
   }

   private sealed interface Source permits Crafter.Craft, Crafter.Smelt, Crafter.Mine {
   }

   static enum Status {
      RUNNING,
      DONE,
      FAILED;
   }

   private static final class Unobtainable extends RuntimeException {
      final String what;

      Unobtainable(String what) {
         super(null, null, false, false);
         this.what = what;
      }
   }
}
