package dev.rex.farmbuilder.modules;

import net.minecraft.class_2338;

final class FarmProtection {
   static boolean homeAnchor(class_2338 origin, class_2338 player) {
      long dx = (long) player.method_10263() - origin.method_10263();
      long dz = (long) player.method_10260() - origin.method_10260();
      long dy = (long) player.method_10264() - origin.method_10264();
      return dx * dx + dz * dz <= 4 && Math.abs(dy) <= 2;
   }

   static boolean locked(boolean correct, boolean inventory, String id, boolean fluid) {
      return correct || inventory || fluid || java.util.Set.of("farmland", "wheat", "carrots", "potatoes", "beetroots",
         "sweet_berry_bush", "sugar_cane", "bamboo", "cactus", "melon_stem", "pumpkin_stem", "attached_melon_stem",
         "attached_pumpkin_stem", "nether_wart", "cocoa", "redstone_wire", "repeater", "comparator", "observer",
         "piston", "sticky_piston", "piston_head", "tripwire", "tripwire_hook").contains(id);
   }
}
