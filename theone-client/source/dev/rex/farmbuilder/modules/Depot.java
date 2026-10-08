package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1707;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2350.class_2353;

final class Depot {
   private static final double REACH = 4.0;
   private final List<class_2338> chests = new ArrayList<>();
   private final Map<class_2338, Map<class_1792, Integer>> seen = new HashMap<>();
   private final Set<class_2338> full = new HashSet<>();
   private final Map<class_2338, class_2338> pairs = new HashMap<>();
   private final List<String> alerts = new ArrayList<>();
   private boolean inspected;
   private boolean finalRead;
   private Depot.Step step = Depot.Step.NONE;
   private boolean withdrawing;
   private boolean ensuring;
   private final Map<class_1792, Integer> wantTargets = new LinkedHashMap<>();
   private Predicate<class_1792> depositWhat = i -> false;
   private final Map<class_1792, Integer> wants = new LinkedHashMap<>();
   private class_2338 anchor;
   private Predicate<class_2338> noPlace = p -> false;
   private class_2338 chest;
   private class_2338 placeAt;
   private Look.Placement placement;
   private final Deque<Runnable> clicks = new ArrayDeque<>();
   private class_1703 handler;
   private final Set<class_2338> visited = new HashSet<>();
   private int ticks;
   private int reserve = 1;
   private int stepStart;
   private int wait;
   private boolean useSent;
   private int useSentAt;
   private int placeTries;
   String failReason;

   /** Free slots a withdrawal must leave in the inventory. */
   void setReserve(int slots) {
      this.reserve = Math.max(1, slots);
   }

   void setNoPlace(Predicate<class_2338> blocked) {
      this.noPlace = blocked;
   }

   boolean isWalking() {
      return this.step == Depot.Step.WALK;
   }

   boolean busy() {
      return this.step != Depot.Step.NONE;
   }

   String status() {
      if (this.step == Depot.Step.NONE) {
         return null;
      } else {
         String what = this.withdrawing ? "Taking farm materials out of storage" : "Storing farm materials";

         return switch (this.step) {
            case WALK -> what + " (walking to the chests)";
            case PLACE -> "Placing a storage chest at the farm";
            default -> what;
         };
      }
   }

   int stored(class_1792 item) {
      int n = 0;

      for (Map<class_1792, Integer> m : this.seen.values()) {
         n += m.getOrDefault(item, 0);
      }

      return n;
   }

   Map<class_1792, Integer> totals() {
      Map<class_1792, Integer> out = new TreeMap<>(Comparator.comparing(i -> i.method_63680().getString()));

      for (Map<class_1792, Integer> m : this.seen.values()) {
         m.forEach((k, v) -> out.merge(k, v, Integer::sum));
      }

      return out;
   }

   void deposit(class_2338 anchor, Predicate<class_1792> what) {
      this.begin(anchor);
      this.withdrawing = false;
      this.ensuring = false;
      this.depositWhat = what;
   }

   void withdraw(class_2338 anchor, Map<class_1792, Integer> want) {
      this.begin(anchor);
      this.withdrawing = true;
      this.ensuring = false;
      this.wants.clear();
      this.wants.putAll(want);
      this.wantTargets.clear();
      want.forEach((item, count) -> this.wantTargets.put(item, inventoryCount(item) + count));
   }

   void ensure(class_2338 anchor) {
      this.begin(anchor);
      this.withdrawing = false;
      this.ensuring = true;
      this.depositWhat = item -> false;
   }

   void discover(class_2338 anchor) {
      this.pruneMissing();
      for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
         int x = anchor.method_10263() + dx, z = anchor.method_10260() + dz;
         if (!MeteorClient.mc.field_1687.method_8393(x >> 4, z >> 4)) continue;
         int surface = MeteorClient.mc.field_1687.method_8624(net.minecraft.class_2902.class_2903.field_13197, x, z);
         java.util.Set<Integer> levels = new java.util.HashSet<>();
         for (int offset = -3; offset <= 3; offset++) {
            levels.add(surface + offset);
            levels.add(anchor.method_10264() + offset);
         }
         for (int y : levels) {
            if (y < MeteorClient.mc.field_1687.method_31607() || y > MeteorClient.mc.field_1687.method_31600()) continue;
            class_2338 p = new class_2338(x, y, z);
            if (!this.noPlace.test(p) && MeteorClient.mc.field_1687.method_8320(p).method_27852(class_2246.field_10034) && !this.chests.contains(p)) this.chests.add(p);
         }
      }
   }

   void forgetContents() { this.seen.clear(); this.full.clear(); this.pairs.clear(); }

   /** Warnings gathered since the last call (missing items, vanished chests). */
   List<String> takeAlerts() {
      if (this.alerts.isEmpty()) return List.of();
      List<String> out = new ArrayList<>(this.alerts);
      this.alerts.clear();
      return out;
   }

   private static String itemName(class_1792 item) {
      return net.minecraft.class_7923.field_41178.method_10221(item).method_12832();
   }

   /** Drops chests that are no longer there; a chest that held items raises an alert. */
   private void pruneMissing() {
      java.util.Iterator<class_2338> it = this.chests.iterator();
      while (it.hasNext()) {
         class_2338 p = it.next();
         if (MeteorClient.mc.field_1687.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)
            && !MeteorClient.mc.field_1687.method_8320(p).method_27852(class_2246.field_10034)) {
            it.remove();
            Map<class_1792, Integer> had = this.seen.remove(p);
            class_2338 partner = this.pairs.remove(p);
            if (partner != null) this.pairs.remove(partner);
            this.full.remove(p);
            int n = had == null ? 0 : had.values().stream().mapToInt(Integer::intValue).sum();
            if (n > 0) this.alerts.add("A supply chest holding " + n + " items is gone (broken or blown up).");
         }
      }
   }

   private Map<class_1792, Integer> contents(class_2338 p) {
      Map<class_1792, Integer> m = this.seen.get(p);
      if (m == null) {
         class_2338 q = this.pairs.get(p);
         if (q != null) m = this.seen.get(q);
      }
      return m;
   }

   private boolean known(class_2338 p) {
      return this.contents(p) != null;
   }

   private boolean isFull(class_2338 p) {
      class_2338 q = this.pairs.get(p);
      return this.full.contains(p) || q != null && this.full.contains(q);
   }

   private void markVisited(class_2338 p) {
      this.visited.add(p);
      class_2338 q = this.pairs.get(p);
      if (q != null) this.visited.add(q);
   }

   private boolean chestAt(class_2338 p) {
      return MeteorClient.mc.field_1687.method_8320(p).method_27852(class_2246.field_10034);
   }

   private boolean touchesChest(class_2338 p) {
      for (class_2350 d : class_2353.field_11062) {
         if (this.chestAt(p.method_10093(d))) return true;
      }
      return false;
   }

   /** True when p touches exactly one chest and that chest has no partner yet, so a chest at p makes a double chest. */
   private boolean pairable(class_2338 p) {
      int touching = 0;
      class_2338 only = null;
      for (class_2350 d : class_2353.field_11062) {
         class_2338 q = p.method_10093(d);
         if (this.chestAt(q)) {
            touching++;
            only = q;
         }
      }
      return touching == 1 && !this.pairs.containsKey(only) && only.method_10264() == p.method_10264();
   }

   private class_2338 partnerOf(class_2338 chest) {
      class_2338 fallback = null;
      for (class_2350 d : class_2353.field_11062) {
         class_2338 n = chest.method_10093(d);
         if (!this.chestAt(n)) continue;
         if (!this.pairs.containsKey(n) || chest.equals(this.pairs.get(n))) return n;
         fallback = n;
      }
      return fallback;
   }

   private static int inventoryCount(class_1792 item) {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         class_1799 stack = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (stack.method_7909() == item) count += stack.method_7947();
      }
      return count;
   }

   private void refreshWants() {
      this.wantTargets.forEach((item, target) -> this.wants.put(item, Math.max(0, target - inventoryCount(item))));
   }

   private void begin(class_2338 anchor) {
      this.anchor = anchor;
      this.discover(anchor);
      this.failReason = null;
      this.visited.clear();
      this.chest = null;
      this.placeTries = 0;
      this.step = Depot.Step.WALK;
      this.stepStart = this.ticks;
   }

   void cancel() {
      if (this.step != Depot.Step.NONE) {
         this.clicks.clear();
         Look.closeScreen();
         IBaritone b = baritone();
         if (b != null) {
            b.getPathingBehavior().cancelEverything();
         }

         this.step = Depot.Step.NONE;
      }
   }

   Depot.Status tick() {
      this.ticks++;
      if (this.step == Depot.Step.NONE) {
         return Depot.Status.DONE;
      } else if (this.wait > 0) {
         this.wait--;
         return Depot.Status.RUNNING;
      } else if (this.ticks - this.stepStart > (this.step == Depot.Step.WALK ? 300 : 90) * 20) {
         return this.fail("Storage trip took too long.");
      } else {
         switch (this.step) {
            case WALK:
               this.tickWalk();
               break;
            case PLACE:
               this.tickPlace();
               break;
            case OPEN:
               this.tickOpen();
               break;
            case CLICK:
               this.tickClick();
         }

         if (this.failReason != null) {
            return Depot.Status.FAILED;
         } else {
            return this.step == Depot.Step.NONE ? Depot.Status.DONE : Depot.Status.RUNNING;
         }
      }
   }

   private void tickWalk() {
      this.pruneMissing();
      if (this.chest == null) {
         this.chest = this.pickChest();
      }

      if (this.chest == null) {
         if (!this.withdrawing && (this.ensuring || this.hasDepositItems())) {
            class_2338 goal = this.anchor;
            if (MeteorClient.mc.field_1724.method_24515().method_10262(goal) > 36.0) {
               this.walkTo(goal);
            } else {
               this.stopWalking();
               this.step = Depot.Step.PLACE;
               this.stepStart = this.ticks;
               this.placeAt = null;
            }
         } else {
            this.finish();
         }
      } else if (this.ensuring) {
         this.finish();
      } else if (this.inReach(this.chest)) {
         this.stopWalking();
         this.step = Depot.Step.OPEN;
         this.stepStart = this.ticks;
         this.useSent = false;
      } else {
         this.walkTo(this.chest);
      }
   }

   private class_2338 pickChest() {
      class_2338 best = null;
      double bestDist = Double.MAX_VALUE;

      for (class_2338 p : this.chests) {
         if (!this.visited.contains(p)
            && (this.ensuring || this.withdrawing || !this.isFull(p))
            && (!this.withdrawing || !this.known(p) || this.wants.entrySet().stream().anyMatch(e -> e.getValue() > 0 && this.contents(p).getOrDefault(e.getKey(), 0) > 0))) {
            double d = MeteorClient.mc.field_1724.method_24515().method_10262(p);
            if (d < bestDist) {
               bestDist = d;
               best = p;
            }
         }
      }

      return best;
   }

   private void tickPlace() {
      if (this.placeAt != null && MeteorClient.mc.field_1687.method_8320(this.placeAt).method_27852(class_2246.field_10034)) {
         this.chests.add(this.placeAt);
         this.chest = this.placeAt;
         this.step = Depot.Step.WALK;
      } else if (!Look.selectHotbar(st -> st.method_7909() == class_1802.field_8106)) {
         int slot = -1;

         for (int i = 9; i < 36; i++) {
            if (MeteorClient.mc.field_1724.method_31548().method_5438(i).method_7909() == class_1802.field_8106) {
               slot = i;
            }
         }

         if (slot < 0) {
            this.fail("No chest to store farm materials in (keep a few chests in your inventory).");
         } else {
            if (Look.openInventoryIfClosed()) {
               Look.clickSwapToHotbar(slot, 6);
            }

            this.wait = 4;
         }
      } else {
         if (MeteorClient.mc.field_1755 != null) {
            Look.closeScreen();
         }

         if (this.placeAt == null || this.placement == null || !MeteorClient.mc.field_1687.method_8320(this.placeAt).method_45474()) {
            this.placeAt = this.findSpot();
            if (this.placeAt == null) {
               if (++this.placeTries > 4) {
                  this.fail("No free spot for a storage chest near the farm.");
                  return;
               }

               int dx = (this.placeTries % 2 == 0 ? 1 : -1) * (2 + this.placeTries);
               int dz = (this.placeTries < 3 ? 1 : -1) * (2 + this.placeTries);
               this.anchor = this.anchor.method_10069(dx, 0, dz);
               this.step = Depot.Step.WALK;
               return;
            }
         }

         Look.lookAt(this.placement.hit(), 12.0F);
         if (Look.crosshairOn(this.placement.against(), this.placement.side())) {
            Look.useCrosshairBlock();
            this.wait = 6;
         }
      }
   }

   private class_2338 findSpot() {
      List<class_2338> candidates = new ArrayList<>();

      // Next to a lone chest first: two chests side by side make one double chest.
      for (class_2338 c : this.chests) {
         if (this.pairs.containsKey(c)) continue;
         for (class_2350 d : class_2353.field_11062) {
            candidates.add(c.method_10093(d));
         }
      }
      for (class_2338 c : this.chests) {
         for (class_2350 d : class_2353.field_11062) {
            candidates.add(c.method_10093(d).method_10093(d));
         }
      }

      class_2338 feet = MeteorClient.mc.field_1724.method_24515();

      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
               if (dx != 0 || dz != 0) {
                  candidates.add(feet.method_10069(dx, dy, dz));
               }
            }
         }
      }

      for (class_2338 p : candidates) {
         if (!this.noPlace.test(p)
            && (!this.touchesChest(p) || this.pairable(p))
            && MeteorClient.mc.field_1687.method_8320(p).method_45474()
            && !MeteorClient.mc.field_1687.method_8320(p.method_10074()).method_26215()
            && MeteorClient.mc.field_1687.method_8320(p.method_10084()).method_45474()
            && !(MeteorClient.mc.field_1724.method_33571().method_1022(class_243.method_24953(p)) > 4.0)
            && !MeteorClient.mc.field_1724.method_5829().method_1009(0.05, 0.0, 0.05).method_994(new class_238(p))) {
            Look.Placement pl = Look.findPlacement(p);
            if (pl != null) {
               this.placement = pl;
               return p;
            }
         }
      }

      return null;
   }

   private void tickOpen() {
      if (MeteorClient.mc.field_1724.field_7512 instanceof class_1707 h) {
         this.handler = h;
         this.inspected = false;
         this.finalRead = false;
         this.step = Depot.Step.CLICK;
         this.wait = 6;
      } else if (this.ticks - this.stepStart > 120) {
         this.markVisited(this.chest);
         this.chest = null;
         this.step = Depot.Step.WALK;
         this.stepStart = this.ticks;
      } else {
         if (this.useSent && this.ticks - this.useSentAt > 25) {
            this.useSent = false;
         }

         if (!this.useSent) {
            Look.lookAt(class_243.method_24953(this.chest), 12.0F);
            if (Look.crosshairOn(this.chest)) {
               Look.expectScreen();
               Look.useCrosshairBlock();
               this.useSent = true;
               this.useSentAt = this.ticks;
            }
         }
      }
   }

   private void queueClicks() {
      this.clicks.clear();

      for (class_1735 s : this.handler.field_7761) {
         int id = s.field_7874;
         boolean player = s.field_7871 instanceof class_1661;
         if (this.withdrawing && !player) {
            this.clicks.add(() -> {
               class_1799 st = this.handler.method_7611(id).method_7677();
               this.refreshWants();
               int need = this.wants.getOrDefault(st.method_7909(), 0);
               if (!st.method_7960() && need > 0 && this.freeSlots() > this.reserve) {
                  this.click(id);
               }
            });
         } else if (!this.withdrawing && player && s.method_34266() < 36) {
            this.clicks.add(() -> {
               class_1799 st = this.handler.method_7611(id).method_7677();
               if (!st.method_7960() && this.depositWhat.test(st.method_7909())) {
                  this.click(id);
               }
            });
         }
      }
   }

   private void tickClick() {
      if (MeteorClient.mc.field_1724.field_7512 != this.handler) {
         this.clicks.clear();
         this.chest = null;
         this.step = Depot.Step.WALK;
      } else if (!this.inspected) {
         // The server's slot contents arrive a moment after the screen opens; compare against the last visit only once they have.
         this.inspected = true;
         Map<class_1792, Integer> before = this.contents(this.chest);
         this.readChest();
         this.checkTamper(before, this.contents(this.chest));
         this.queueClicks();
         this.wait = 2;
      } else {
         Runnable next = this.clicks.poll();
         if (next != null) {
            next.run();
            this.wait = 2 + (int)(Math.random() * 3.0);
         } else if (!this.finalRead) {
            this.finalRead = true;
            this.wait = 10;
         } else {
            if (this.withdrawing) this.refreshWants();
            this.readChest();
            Look.closeScreen();
            this.markVisited(this.chest);
            if (!this.withdrawing && this.hasDepositItems()) {
               this.full.add(this.chest);
            }

            this.chest = null;
            boolean done = this.withdrawing ? this.wants.values().stream().allMatch(v -> v <= 0) || this.freeSlots() <= this.reserve : !this.hasDepositItems();
            if (done) {
               this.finish();
            } else {
               this.step = Depot.Step.WALK;
               this.stepStart = this.ticks;
            }

            this.wait = 5;
         }
      }
   }

   private void checkTamper(Map<class_1792, Integer> before, Map<class_1792, Integer> after) {
      Map<class_1792, Integer> gone = ChestWatch.shortfall(before, after);
      if (!gone.isEmpty()) {
         this.alerts.add("Items are missing from the supply chest since my last visit: " + ChestWatch.describe(gone, Depot::itemName) + ". Someone may have taken them.");
      }
   }

   private void readChest() {
      Map<class_1792, Integer> m = new HashMap<>();
      int free = 0;
      int slots = 0;

      for (class_1735 s : this.handler.field_7761) {
         if (!(s.field_7871 instanceof class_1661)) {
            slots++;
            class_1799 st = s.method_7677();
            if (st.method_7960()) {
               free++;
            } else {
               m.merge(st.method_7909(), st.method_7947(), Integer::sum);
            }
         }
      }

      // A double chest is one inventory seen through two blocks; record it once so item counts are not doubled.
      class_2338 partner = slots >= 54 ? this.partnerOf(this.chest) : null;
      if (partner != null) {
         this.pairs.put(this.chest, partner);
         this.pairs.put(partner, this.chest);
         this.seen.remove(partner);
         this.full.remove(partner);
         if (!this.chests.contains(partner)) this.chests.add(partner);
      } else {
         class_2338 old = this.pairs.remove(this.chest);
         if (old != null) this.pairs.remove(old);
      }
      this.seen.put(this.chest, m);
      if (free > 0) {
         this.full.remove(this.chest);
      }
   }

   private boolean hasDepositItems() {
      for (int i = 0; i < 36; i++) {
         class_1799 st = MeteorClient.mc.field_1724.method_31548().method_5438(i);
         if (!st.method_7960() && this.depositWhat.test(st.method_7909())) {
            return true;
         }
      }

      return false;
   }

   private int freeSlots() {
      int n = 0;

      for (int i = 0; i < 36; i++) {
         if (MeteorClient.mc.field_1724.method_31548().method_5438(i).method_7960()) {
            n++;
         }
      }

      return n;
   }

   private void click(int slot) {
      MeteorClient.mc.field_1761.method_2906(this.handler.field_7763, slot, 0, class_1713.field_7794, MeteorClient.mc.field_1724);
   }

   private boolean inReach(class_2338 p) {
      return MeteorClient.mc.field_1724.method_33571().method_1022(class_243.method_24953(p)) <= 4.0;
   }

   private void walkTo(class_2338 p) {
      IBaritone b = baritone();
      if (b != null && !b.getCustomGoalProcess().isActive()) {
         b.getCustomGoalProcess().setGoalAndPath(new GoalNear(p, 2));
      }
   }

   private void stopWalking() {
      IBaritone b = baritone();
      if (b != null && b.getCustomGoalProcess().isActive()) {
         b.getPathingBehavior().cancelEverything();
      }
   }

   private void finish() {
      this.stopWalking();
      if (MeteorClient.mc.field_1755 != null) {
         Look.closeScreen();
      }

      this.step = Depot.Step.NONE;
   }

   private Depot.Status fail(String why) {
      this.failReason = why;
      this.finish();
      return Depot.Status.FAILED;
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable var1) {
         return null;
      }
   }

   String serialize() {
      StringBuilder sb = new StringBuilder();

      for (class_2338 p : this.chests) {
         if (sb.length() > 0) {
            sb.append(';');
         }

         sb.append(p.method_10263()).append(',').append(p.method_10264()).append(',').append(p.method_10260());
      }

      return sb.toString();
   }

   void load(String s) {
      this.chests.clear();
      this.seen.clear();
      this.full.clear();
      this.pairs.clear();
      this.alerts.clear();
      if (s != null && !s.isBlank()) {
         for (String part : s.split(";")) {
            String[] c = part.split(",");
            if (c.length == 3) {
               this.chests.add(new class_2338(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
            }
         }
      }
   }

   void clear() {
      this.chests.clear();
      this.seen.clear();
      this.full.clear();
      this.pairs.clear();
      this.alerts.clear();
   }

   boolean hasUnknown() {
      for (class_2338 p : this.chests) {
         if (!this.known(p)) {
            return true;
         }
      }

      return false;
   }

   int chestCount() {
      return this.chests.size();
   }

   static enum Status {
      RUNNING,
      DONE,
      FAILED;
   }

   private static enum Step {
      NONE,
      WALK,
      PLACE,
      OPEN,
      CLICK;
   }
}
