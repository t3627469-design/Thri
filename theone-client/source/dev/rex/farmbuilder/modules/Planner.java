package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.class_1792;
import net.minecraft.class_243;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2680;

/**
 * Decides what to place next.
 *
 * The build is kept in horizontal layers and the active layer is the lowest one that has a block that can really be
 * placed now: one that has something solid to attach to, whose material is available and that is not parked. Only those
 * "open" blocks are ever ranked, so a floor hanging over a hole or a wall that waits on a lower layer can't make the
 * bot stand still. Inside the layer the next block is the one that needs the least turning (and the fewest hotbar
 * swaps), and the one being aimed at is kept until it is placed.
 */
final class Planner {
   enum Kind { PLACE, WALK, NONE }

   /** Why there is nothing to do: everything left needs a material that isn't there, can't attach to anything yet, or is parked. */
   enum Why { DONE, MATERIALS, LONELY, STUCK, WAITING }

   /** The answer to "what now": place this block, walk toward this block, or there is nothing to do. */
   static final class Choice {
      final Kind kind;
      final class_2338 pos;
      final Look.Placement placement;
      /** The block to place is inside the player's own box. */
      final boolean underUs;
      final Why why;

      private Choice(Kind kind, class_2338 pos, Look.Placement placement, boolean underUs, Why why) {
         this.kind = kind;
         this.pos = pos;
         this.placement = placement;
         this.underUs = underUs;
         this.why = why;
      }

      static Choice none(Why why) {
         return new Choice(Kind.NONE, null, null, false, why);
      }

      static Choice place(class_2338 p, Look.Placement placement) {
         return new Choice(Kind.PLACE, p, placement, false, Why.WAITING);
      }

      static Choice walk(class_2338 p, boolean underUs) {
         return new Choice(Kind.WALK, p, null, underUs, Why.WAITING);
      }
   }

   /**
    * For speed 1 (careful) to 5 (as fast as it goes): ticks between placements (low, high), degrees turned per
    * step, and how many aiming steps are taken per tick.
    */
   static int[] speedProfile(int speed) {
      return switch (Math.max(1, Math.min(5, speed))) {
         case 1 -> new int[] {6, 10, 9, 1};
         case 2 -> new int[] {4, 6, 14, 1};
         case 3 -> new int[] {3, 5, 20, 1};
         case 4 -> new int[] {2, 3, 30, 2};
         default -> new int[] {1, 2, 45, 3};
      };
   }

   private static final int NEAR = 8;
   /** Ticks before a layer's list of placeable blocks is rebuilt from scratch (it is kept up to date as blocks are placed). */
   private static final int REFRESH = 300;
   /** Ticks between looks at every layer while nothing can be done. */
   private static final int IDLE_SCAN = 40;
   private static final int REVERIFY = 15;
   private static final int HOLD_TICKS = 40;
   private static final int LONELY_GIVE_UP = 1200;

   private static final class LayerState {
      final ArrayList<class_2338> open = new ArrayList<>();
      final Set<class_2338> openSet = new HashSet<>();
      int tick = -1000;
      int unavailable;
      int lonely;
      int parked;
      class_2338 unloaded;
      double unloadedD2;
   }

   private record Removed(class_2338 pos, int tick) {
   }

   private final Map<class_2338, class_2680> wanted;
   private final TreeMap<Integer, ArrayList<class_2338>> layers = new TreeMap<>();
   /** Every position still to place. */
   private final Set<class_2338> remaining = new HashSet<>();
   private final Map<Integer, LayerState> states = new HashMap<>();
   /** Long rests: couldn't be reached or placed, and not retried until the time is up. Placing something else does not clear them. */
   private final Map<class_2338, Integer> parked = new HashMap<>();
   /** Short rests: no clickable face from here right now. */
   private final Map<class_2338, Integer> retry = new HashMap<>();
   private final Map<class_2338, Integer> strikes = new HashMap<>();
   /** Blocks swept as built, looked at again a little later in case the server took the placement back. */
   private final ArrayDeque<Removed> removed = new ArrayDeque<>();
   /** Blocks just clicked, looked at again in a few ticks to see whether they were placed. */
   private final ArrayDeque<Removed> clicked = new ArrayDeque<>();
   private int lastFullSweep;
   private int activeY = Integer.MIN_VALUE;
   private int size;
   private int minY = Integer.MAX_VALUE;
   private int maxY = Integer.MIN_VALUE;
   private int tick;
   private int scanTick = -1000;
   private boolean rescan = true;
   private int lonelySince = -1;
   private Why lastWhy = Why.WAITING;
   private class_2338 cur;
   private int curTicks;
   private class_2338 walkTo;
   /** A block in a chunk that isn't loaded yet: walk toward it until the chunk shows up. */
   private class_2338 unloadedWalk;
   /** Blocks dropped because something else that can't be replaced was already there. */
   int blocked;
   /** Blocks given up on: nothing ever turned up to attach them to, or they could not be placed after three tries. */
   int skipped;

   Planner(Map<class_2338, class_2680> wanted) {
      this.wanted = wanted;
   }

   void clear() {
      this.layers.clear();
      this.remaining.clear();
      this.states.clear();
      this.parked.clear();
      this.retry.clear();
      this.strikes.clear();
      this.removed.clear();
      this.clicked.clear();
      this.activeY = Integer.MIN_VALUE;
      this.size = 0;
      this.minY = Integer.MAX_VALUE;
      this.maxY = Integer.MIN_VALUE;
      this.blocked = 0;
      this.skipped = 0;
      this.scanTick = -1000;
      this.rescan = true;
      this.lonelySince = -1;
      this.lastWhy = Why.WAITING;
      this.cur = null;
      this.walkTo = null;
      this.unloadedWalk = null;
      this.curTicks = 0;
   }

   void add(class_2338 p) {
      int y = p.method_10264();
      this.layers.computeIfAbsent(y, k -> new ArrayList<>()).add(p);
      this.remaining.add(p);
      this.size++;
      this.minY = Math.min(this.minY, y);
      this.maxY = Math.max(this.maxY, y);
   }

   int size() {
      return this.size;
   }

   boolean isEmpty() {
      return this.size == 0;
   }

   /** World Y of the layer being worked on, or Integer.MIN_VALUE when there is none. */
   int activeLayerY() {
      return this.activeY;
   }

   /** True while the position is still on the list to be placed. */
   boolean pending(class_2338 p) {
      return this.remaining.contains(p);
   }

   /** The next layer after the one being worked on that still has blocks, or Integer.MIN_VALUE. */
   int nextLayerY() {
      int from = this.activeY != Integer.MIN_VALUE ? this.activeY : this.lowestLayerY();
      if (from == Integer.MIN_VALUE) return Integer.MIN_VALUE;
      Integer n = this.layers.higherKey(from);
      return n == null ? Integer.MIN_VALUE : n;
   }

   /** How many blocks the layer at world Y still has. */
   int blocksIn(int y) {
      ArrayList<class_2338> l = this.layers.get(y);
      return l == null ? 0 : l.size();
   }

   /** The lowest layer that still has blocks, or Integer.MIN_VALUE. */
   int lowestLayerY() {
      return this.layers.isEmpty() ? Integer.MIN_VALUE : this.layers.firstKey();
   }

   /** 1-based number of the layer being worked on (or the lowest one left) counted from the bottom of the schematic. */
   int layerNumber() {
      int y = this.activeY != Integer.MIN_VALUE ? this.activeY : this.lowestLayerY();
      return y == Integer.MIN_VALUE ? 0 : y - this.minY + 1;
   }

   int layerCount() {
      return this.size == 0 ? 0 : this.maxY - this.minY + 1;
   }

   /** The positions still to place in the layer being worked on (the lowest one before work starts). Live list, do not modify. */
   List<class_2338> view() {
      int y = this.activeY != Integer.MIN_VALUE ? this.activeY : this.lowestLayerY();
      ArrayList<class_2338> l = y == Integer.MIN_VALUE ? null : this.layers.get(y);
      return l == null ? List.of() : l;
   }

   /** The first n remaining positions: the active layer nearest the player first, then the layers above. */
   List<class_2338> upcoming(int n) {
      List<class_2338> out = new ArrayList<>();
      int first = this.activeY != Integer.MIN_VALUE ? this.activeY : this.lowestLayerY();
      ArrayList<class_2338> l = first == Integer.MIN_VALUE ? null : this.layers.get(first);
      if (l != null) {
         class_2338 here = MeteorClient.mc.field_1724.method_24515();
         List<class_2338> copy = new ArrayList<>(l);
         copy.sort(Comparator.comparingDouble(p -> p.method_10262(here)));
         for (class_2338 p : copy) {
            out.add(p);
            if (out.size() >= n) return out;
         }
      }
      for (Map.Entry<Integer, ArrayList<class_2338>> e : this.layers.entrySet()) {
         if (e.getKey() == first) continue;
         for (class_2338 p : e.getValue()) {
            out.add(p);
            if (out.size() >= n) return out;
         }
      }
      return out;
   }

   /** Remaining blocks that are not placed yet, grouped by item. */
   Map<class_1792, Integer> remainingItems() {
      Map<class_1792, Integer> out = new LinkedHashMap<>();
      var world = MeteorClient.mc.field_1687;
      for (ArrayList<class_2338> l : this.layers.values()) {
         for (class_2338 p : l) {
            if (world.method_8320(p).method_26204() == this.wanted.get(p).method_26204()) continue;
            out.merge(this.item(p), 1, Integer::sum);
         }
      }
      return out;
   }

   /** Blocks being rested right now (couldn't be reached or placed for the moment). */
   int deferredCount() {
      int n = 0;
      for (Integer until : this.parked.values()) if (until > this.tick) n++;
      for (Integer until : this.retry.values()) if (until > this.tick) n++;
      return n;
   }

   private class_1792 item(class_2338 p) {
      return this.wanted.get(p).method_26204().method_8389();
   }

   private boolean resting(class_2338 p) {
      if (!this.parked.isEmpty()) {
         Integer until = this.parked.get(p);
         if (until != null) {
            if (until > this.tick) return true;
            this.parked.remove(p);
         }
      }
      if (!this.retry.isEmpty()) {
         Integer until = this.retry.get(p);
         if (until != null) {
            if (until > this.tick) return true;
            this.retry.remove(p);
         }
      }
      return false;
   }

   // ---------------------------------------------------------------- bookkeeping

   void drop(class_2338 p) {
      int y = p.method_10264();
      ArrayList<class_2338> l = this.layers.get(y);
      if (l != null && l.remove(p)) {
         this.size--;
         this.remaining.remove(p);
         this.parked.remove(p);
         this.retry.remove(p);
         this.strikes.remove(p);
         this.removeOpen(this.states.get(y), p);
         if (p.equals(this.cur)) this.cur = null;
         if (p.equals(this.walkTo)) this.walkTo = null;
         if (l.isEmpty()) {
            this.layers.remove(y);
            this.states.remove(y);
            if (this.activeY == y) this.activeY = Integer.MIN_VALUE;
         }
      }
   }

   private void removeOpen(LayerState st, class_2338 p) {
      if (st != null && st.openSet.remove(p)) st.open.remove(p);
   }

   private void addOpen(LayerState st, class_2338 p) {
      if (st != null && st.openSet.add(p)) st.open.add(p);
   }

   /** Rest this block for a while and look elsewhere. */
   void park(class_2338 p, int ticks) {
      this.parked.put(p, this.tick + ticks);
      this.removeOpen(this.states.get(p.method_10264()), p);
      if (p.equals(this.cur)) this.cur = null;
      if (p.equals(this.walkTo)) this.walkTo = null;
   }

   /** The caller got nowhere with this block (walking, aiming): rest it a while, and give up on it the third time. */
   void stuck(class_2338 p) {
      int n = this.strikes.merge(p, 1, Integer::sum);
      if (n >= 3) {
         this.drop(p);
         this.skipped++;
      } else {
         this.park(p, 400);
      }
   }

   /** Call right after a placement click. */
   void placed(class_2338 p) {
      this.clicked.add(new Removed(p, this.tick));
      this.removeOpen(this.states.get(p.method_10264()), p);
      // the neighbours of a new block now have something to attach to
      for (class_2350 d : class_2350.values()) {
         class_2338 n = p.method_10093(d);
         if (!this.remaining.contains(n) || this.resting(n)) continue;
         this.addOpen(this.states.get(n.method_10264()), n);
      }
      this.cur = null;
      this.walkTo = null;
      this.rescan = true;
   }

   /**
    * Bookkeeping, called every few ticks: blocks that were just clicked are checked and taken off the list once they
    * are there, and now and then the whole layer is checked in case something changed without us.
    */
   void sweep() {
      var world = MeteorClient.mc.field_1687;
      while (!this.clicked.isEmpty() && this.tick - this.clicked.peekFirst().tick() >= 2) {
         Removed r = this.clicked.pollFirst();
         class_2338 p = r.pos();
         if (!this.remaining.contains(p)) continue;
         if (!world.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) continue;
         class_2680 have = world.method_8320(p);
         if (have.method_26204() == this.wanted.get(p).method_26204()) {
            this.finish(p, false);
         } else if (!have.method_45474()) {
            this.finish(p, true);
         } else if (this.tick - r.tick() < 12) {
            this.clicked.addFirst(r);
            break;
         }
      }
      if (this.activeY != Integer.MIN_VALUE && this.tick - this.lastFullSweep >= 100) {
         this.lastFullSweep = this.tick;
         this.sweepLayer(this.activeY);
      }
   }

   /** The block is there (or something else is, which can't be replaced): off the list. */
   private void finish(class_2338 p, boolean blockedByOther) {
      int y = p.method_10264();
      ArrayList<class_2338> l = this.layers.get(y);
      if (l == null || !l.remove(p)) return;
      this.size--;
      this.remaining.remove(p);
      this.parked.remove(p);
      this.retry.remove(p);
      this.strikes.remove(p);
      if (blockedByOther) this.blocked++;
      else this.removed.add(new Removed(p, this.tick));
      if (l.isEmpty()) {
         this.layers.remove(y);
         this.states.remove(y);
         if (this.activeY == y) this.activeY = Integer.MIN_VALUE;
      }
   }

   private void sweepLayer(int y) {
      ArrayList<class_2338> l = this.layers.get(y);
      if (l == null) return;
      var world = MeteorClient.mc.field_1687;
      int[] gone = new int[2];
      l.removeIf(p -> {
         if (!world.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) return false;
         class_2680 have = world.method_8320(p);
         if (have.method_26204() == this.wanted.get(p).method_26204()) {
            gone[0]++;
            this.remaining.remove(p);
            this.parked.remove(p);
            this.retry.remove(p);
            this.strikes.remove(p);
            this.removed.add(new Removed(p, this.tick));
            return true;
         }
         if (!have.method_45474()) {
            gone[0]++;
            gone[1]++;
            this.remaining.remove(p);
            this.parked.remove(p);
            this.retry.remove(p);
            this.strikes.remove(p);
            return true;
         }
         return false;
      });
      this.size -= gone[0];
      this.blocked += gone[1];
      if (l.isEmpty()) {
         this.layers.remove(y);
         this.states.remove(y);
         if (this.activeY == y) this.activeY = Integer.MIN_VALUE;
      }
   }

   /** A block counted as built that the server took back (rollback, lag, anticheat) goes back on the list. */
   private void reverify() {
      var world = MeteorClient.mc.field_1687;
      while (!this.removed.isEmpty() && this.tick - this.removed.peekFirst().tick() >= REVERIFY) {
         Removed r = this.removed.pollFirst();
         class_2338 p = r.pos();
         if (!world.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) continue;
         class_2680 have = world.method_8320(p);
         if (have.method_26204() != this.wanted.get(p).method_26204() && have.method_45474()) {
            this.add(p);
            LayerState st = this.states.get(p.method_10264());
            if (st != null) st.tick = -1000;
            this.rescan = true;
         }
      }
   }

   /** True when some neighbour of p could be clicked to place a block there (the same test as Look.findPlacement, without reach or line of sight). */
   static boolean hasSupport(class_2338 p) {
      var world = MeteorClient.mc.field_1687;
      for (class_2350 d : class_2350.values()) {
         class_2338 n = p.method_10093(d);
         class_2680 st = world.method_8320(n);
         if (!st.method_26215() && !st.method_45474() && st.method_26227().method_15769()
            && !st.method_26220(world, n).method_1110() && !BlockUtils.isClickable(st.method_26204())) return true;
      }
      return false;
   }

   // ---------------------------------------------------------------- choosing

   /** Sorts the layer into blocks that can be placed now (open) and those that can't, and counts why. */
   private LayerState refresh(int y, Predicate<class_1792> available) {
      this.sweepLayer(y);
      LayerState st = this.states.computeIfAbsent(y, k -> new LayerState());
      st.open.clear();
      st.openSet.clear();
      st.unavailable = 0;
      st.lonely = 0;
      st.parked = 0;
      st.unloaded = null;
      st.unloadedD2 = Double.MAX_VALUE;
      st.tick = this.tick;
      ArrayList<class_2338> l = this.layers.get(y);
      if (l == null) return st;
      var mc = MeteorClient.mc;
      var world = mc.field_1687;
      double px = mc.field_1724.method_23317();
      double pz = mc.field_1724.method_23321();
      for (class_2338 p : l) {
         if (!world.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) {
            double dx = p.method_10263() + 0.5 - px;
            double dz = p.method_10260() + 0.5 - pz;
            double d2 = dx * dx + dz * dz;
            if (d2 < st.unloadedD2) {
               st.unloadedD2 = d2;
               st.unloaded = p;
            }
            continue;
         }
         if (this.resting(p)) {
            st.parked++;
            continue;
         }
         if (!available.test(this.item(p))) {
            st.unavailable++;
            continue;
         }
         if (!hasSupport(p)) {
            st.lonely++;
            continue;
         }
         st.open.add(p);
         st.openSet.add(p);
      }
      return st;
   }

   /**
    * Picks the next block. {@code available} says whether the material for an item can be used right now and
    * {@code swapCost} how much it costs to get the item into the hand (lower is better; in hand is negative).
    * A placement is returned as PLACE, something to walk to as WALK, and when there is nothing to do NONE with the reason.
    */
   Choice choose(Predicate<class_1792> available, ToDoubleFunction<class_1792> swapCost, double reach) {
      this.tick++;
      this.reverify();
      if (this.size == 0) return Choice.none(Why.DONE);
      Choice c = this.work(available, swapCost, reach);
      if (c != null) {
         this.lonelySince = -1;
         return c;
      }
      if (this.unloadedWalk != null) {
         if (!MeteorClient.mc.field_1687.method_8393(this.unloadedWalk.method_10263() >> 4, this.unloadedWalk.method_10260() >> 4)
            && this.layers.containsKey(this.unloadedWalk.method_10264())) return Choice.walk(this.unloadedWalk, false);
         this.unloadedWalk = null;
         this.rescan = true;
      }
      // Nothing left to do in this layer: look at all of them (at most every few ticks, or right away after a placement).
      if (this.tick - this.scanTick < IDLE_SCAN && !this.rescan) return Choice.none(this.lastWhy);
      this.scanTick = this.tick;
      this.rescan = false;
      this.activeY = Integer.MIN_VALUE;
      this.cur = null;
      this.walkTo = null;
      int unavailable = 0;
      int lonely = 0;
      int parkedCount = 0;
      class_2338 unloaded = null;
      double unloadedD2 = Double.MAX_VALUE;
      for (Integer y : new ArrayList<>(this.layers.keySet())) {
         if (!this.layers.containsKey(y)) continue;
         LayerState st = this.refresh(y, available);
         if (!st.open.isEmpty()) {
            this.activeY = y;
            c = this.work(available, swapCost, reach);
            if (c != null) {
               this.lonelySince = -1;
               return c;
            }
            this.activeY = Integer.MIN_VALUE;
         }
         unavailable += st.unavailable;
         lonely += st.lonely;
         parkedCount += st.parked;
         if (st.unloaded != null && st.unloadedD2 < unloadedD2) {
            unloadedD2 = st.unloadedD2;
            unloaded = st.unloaded;
         }
      }
      if (this.size == 0) return Choice.none(Why.DONE);
      if (unloaded != null) {
         this.lonelySince = -1;
         this.unloadedWalk = unloaded;
         return Choice.walk(unloaded, false);
      }
      Why why = unavailable > 0 ? Why.MATERIALS : parkedCount > 0 ? Why.STUCK : lonely > 0 ? Why.LONELY : Why.WAITING;
      if (why == Why.LONELY) {
         if (this.lonelySince < 0) this.lonelySince = this.tick;
         if (this.tick - this.lonelySince >= LONELY_GIVE_UP) {
            // everything left has nothing to attach to and nothing else is pending: give up on it so the run can end
            this.skipped += this.size;
            this.layers.clear();
            this.remaining.clear();
            this.states.clear();
            this.size = 0;
            this.activeY = Integer.MIN_VALUE;
            return Choice.none(Why.DONE);
         }
      } else {
         this.lonelySince = -1;
      }
      this.lastWhy = why;
      return Choice.none(why);
   }

   /** One pass over the open blocks of the active layer; null when none of them can be used. */
   private Choice work(Predicate<class_1792> available, ToDoubleFunction<class_1792> swapCost, double reach) {
      if (this.activeY == Integer.MIN_VALUE) return null;
      if (!this.layers.containsKey(this.activeY)) {
         this.activeY = Integer.MIN_VALUE;
         return null;
      }
      LayerState st = this.states.get(this.activeY);
      if (st == null || this.tick - st.tick >= REFRESH) st = this.refresh(this.activeY, available);
      if (st.open.isEmpty()) return null;
      var mc = MeteorClient.mc;
      class_243 eye = mc.field_1724.method_33571();
      double r2 = reach * reach;
      double px = mc.field_1724.method_23317();
      double py = mc.field_1724.method_23318();
      double pz = mc.field_1724.method_23321();

      // Keep working on the block already being aimed at.
      if (this.cur != null) {
         class_2338 c = this.cur;
         if (this.curTicks++ < HOLD_TICKS && this.usable(c, available) && this.inReach(c, eye, r2) && !overlapsPlayer(c, px, py, pz)) {
            Look.Placement pl = Look.findPlacement(c);
            if (pl != null) return Choice.place(c, pl);
         }
         this.cur = null;
      }

      // Rank the open blocks in reach by how little the player has to turn and swap; remember the nearest one out of reach.
      double yaw = Math.toRadians(mc.field_1724.method_36454());
      double pitch = Math.toRadians(mc.field_1724.method_36455());
      double lx = -Math.sin(yaw) * Math.cos(pitch);
      double ly = -Math.sin(pitch);
      double lz = Math.cos(yaw) * Math.cos(pitch);
      class_2338[] near = new class_2338[NEAR];
      double[] nearScore = new double[NEAR];
      int nn = 0;
      class_2338 far = null;
      double farD2 = Double.MAX_VALUE;
      class_2338 underUs = null;
      for (class_2338 p : st.open) {
         double dx = p.method_10263() + 0.5 - eye.field_1352;
         double dy = p.method_10264() + 0.5 - eye.field_1351;
         double dz = p.method_10260() + 0.5 - eye.field_1350;
         double d2 = dx * dx + dy * dy + dz * dz;
         if (d2 > r2) {
            if (d2 < farD2) {
               farD2 = d2;
               far = p;
            }
            continue;
         }
         if (overlapsPlayer(p, px, py, pz)) {
            underUs = p;
            continue;
         }
         double d = Math.sqrt(d2);
         double dot = d < 1.0E-6 ? 1.0 : (dx * lx + dy * ly + dz * lz) / d;
         double score = d2 * 0.15 + (1.0 - dot) * 25.0 + swapCost.applyAsDouble(this.item(p));
         int at = nn;
         while (at > 0 && nearScore[at - 1] > score) at--;
         if (at < NEAR) {
            int end = Math.min(nn, NEAR - 1);
            for (int i = end; i > at; i--) {
               near[i] = near[i - 1];
               nearScore[i] = nearScore[i - 1];
            }
            near[at] = p;
            nearScore[at] = score;
            if (nn < NEAR) nn++;
         }
      }

      class_2338 inReach = null;
      for (int i = 0; i < nn; i++) {
         class_2338 p = near[i];
         if (!this.usable(p, available)) {
            // built, taken or used up since the list was sorted
            this.removeOpen(st, p);
            continue;
         }
         Look.Placement placement = Look.findPlacement(p);
         if (placement != null) {
            this.cur = p;
            this.curTicks = 0;
            this.walkTo = null;
            return Choice.place(p, placement);
         }
         // supported but no face to click from here: walk around it; the others get a short break
         if (inReach == null) {
            inReach = p;
         } else {
            this.retry.put(p, this.tick + 8);
            this.removeOpen(st, p);
         }
      }

      // Walk toward the nearest block out of reach, and stay on the same one until it is reached.
      if (this.walkTo != null && (!this.usable(this.walkTo, available) || this.inReach(this.walkTo, eye, r2))) this.walkTo = null;
      if (this.walkTo == null && far != null) {
         if (this.usable(far, available)) this.walkTo = far;
         else this.removeOpen(st, far);
      }
      if (inReach != null) return Choice.walk(inReach, false);
      if (this.walkTo != null) return Choice.walk(this.walkTo, false);
      if (underUs != null) return Choice.walk(underUs, true);
      return null;
   }

   /** Still to place, still wanted, material available, not resting, and something to attach to. */
   private boolean usable(class_2338 p, Predicate<class_1792> available) {
      var world = MeteorClient.mc.field_1687;
      if (!world.method_8393(p.method_10263() >> 4, p.method_10260() >> 4)) return false;
      if (!world.method_8320(p).method_45474()) return false;
      if (this.resting(p)) return false;
      if (!available.test(this.item(p))) return false;
      return hasSupport(p);
   }

   private boolean inReach(class_2338 p, class_243 eye, double r2) {
      double dx = p.method_10263() + 0.5 - eye.field_1352;
      double dy = p.method_10264() + 0.5 - eye.field_1351;
      double dz = p.method_10260() + 0.5 - eye.field_1350;
      return dx * dx + dy * dy + dz * dz <= r2;
   }

   /** Vanilla refuses a block whose box overlaps the player's (0.6 wide, 1.8 tall), not only the feet and head cells. */
   private static boolean overlapsPlayer(class_2338 p, double px, double py, double pz) {
      return p.method_10263() < px + 0.3 && p.method_10263() + 1 > px - 0.3
         && p.method_10260() < pz + 0.3 && p.method_10260() + 1 > pz - 0.3
         && p.method_10264() < py + 1.8 && p.method_10264() + 1 > py;
   }
}
