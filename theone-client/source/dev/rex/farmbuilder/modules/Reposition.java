package dev.rex.farmbuilder.modules;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.class_2338;

/**
 * Handles a block that is supported and close but can't be clicked from where the player stands (or sits inside the
 * player's own box). Baritone gets ONE move at a time, to a different side of the block each time, and is left alone
 * while it carries that move out, instead of being told the same goal every tick (which made it recalculate its path
 * over and over). After three moves that didn't help, the caller gives up on the block for a while.
 */
final class Reposition {
   private static final int GRACE_TICKS = 8;
   private static final int MAX_MOVES = 3;
   private static final int[][] SIDES = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

   private class_2338 goal;
   private int ticks;
   private int moves;
   private class_2338 flightSpot;
   private int flightTicks;

   void reset() {
      this.goal = null;
      this.ticks = 0;
      this.moves = 0;
      this.flightSpot = null;
      this.flightTicks = 0;
   }

   /** One tick; true when the block should be given up on. */
   boolean tick(IBaritone b, class_2338 target, boolean underUs, Approach flyer) {
      if (!target.equals(this.goal)) {
         this.goal = target;
         this.ticks = 0;
         this.moves = 0;
         this.flightSpot = null;
      }
      boolean fly = flyer != null && Approach.canFly();
      // a move in progress: flight is flown here, walking is left to Baritone
      if (fly && this.flightSpot != null) {
         flyer.fly(this.flightSpot, 1.2);
         if (flyer.flewThere() || ++this.flightTicks > 60) {
            this.flightSpot = null;
            flyer.stop();
            this.ticks = 0;
         }
         return false;
      }
      if (!fly && b != null && b.getCustomGoalProcess().isActive()) return false;
      this.ticks++;
      if (this.ticks < GRACE_TICKS) return false;
      if (this.moves >= MAX_MOVES) {
         this.reset();
         return true;
      }
      int[] side = SIDES[this.moves++ % SIDES.length];
      int dist = underUs ? 4 : 3;
      if (fly) {
         this.flightSpot = target.method_10069(side[0] * dist, 1, side[1] * dist);
         this.flightTicks = 0;
      } else if (b != null) {
         b.getCustomGoalProcess().setGoalAndPath(new GoalNear(target.method_10069(side[0] * dist, 0, side[1] * dist), 1));
      }
      this.ticks = 0;
      return false;
   }
}
