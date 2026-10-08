package dev.rex.farmbuilder.modules;

import net.minecraft.class_2338;

final class JunkLogic {
   static boolean localSpot(class_2338 feet, class_2338 spot) {
      if (feet == null || spot == null) return false;
      double distance = feet.method_10262(spot);
      return distance >= 9 && distance <= 400 && Math.abs((long) feet.method_10264() - spot.method_10264()) <= 2;
   }

   static boolean returned(class_2338 feet, class_2338 start, class_2338 drop) {
      return feet != null && start != null && drop != null && feet.method_10262(start) <= 2.25 && feet.method_10262(drop) >= 6.25;
   }
}
