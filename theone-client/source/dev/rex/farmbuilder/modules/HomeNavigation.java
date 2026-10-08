package dev.rex.farmbuilder.modules;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import net.minecraft.class_2338;

final class HomeNavigation {
   static void walk(IBaritone baritone, class_2338 origin, int radius) {
      if (baritone == null || origin == null) return;
      var process = baritone.getCustomGoalProcess();
      GoalNear goal = new GoalNear(origin, radius);
      if (!process.isActive() || !goal.equals(process.getGoal())) process.setGoalAndPath(goal);
   }

   static boolean preferCommand(boolean gathering, boolean quick, String command, double distance, int minimum) {
      return command != null && !command.isBlank() && (gathering || quick && Double.isFinite(distance) && distance >= minimum);
   }
}
