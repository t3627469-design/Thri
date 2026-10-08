package dev.thri.module.modules.player;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public class AutoArmor extends Module {
    private long last;
    public AutoArmor() { super("AutoArmor", Category.PLAYER, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        long now = System.currentTimeMillis();
        if (now - last < 120) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            int armorSlotId = switch (slot) {
                case HEAD  -> 5;
                case CHEST -> 6;
                case LEGS  -> 7;
                case FEET  -> 8;
                default -> -1;
            };
            if (armorSlotId < 0) continue;
            ItemStack cur = mc.player.getEquippedStack(slot);
            double bestScore = score(cur, slot);
            int bestInv = -1;
            for (int i = 9; i < 45; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                double sc = score(s, slot);
                if (sc > bestScore) { bestScore = sc; bestInv = i; }
            }
            if (bestInv >= 0) {
                int sync = mc.player.playerScreenHandler.syncId;
                mc.interactionManager.clickSlot(sync, bestInv,     0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, armorSlotId, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(sync, bestInv,     0, SlotActionType.PICKUP, mc.player);
                last = now;
                return;
            }
        }
    }

    private double score(ItemStack s, EquipmentSlot slot) {
        if (s.isEmpty()) return -1;
        EquippableComponent eq = s.get(DataComponentTypes.EQUIPPABLE);
        if (eq == null || eq.slot() != slot) return -1;
        int tier = tierFor(s);
        double durability = s.getMaxDamage() == 0 ? 1 : 1.0 - (double) s.getDamage() / s.getMaxDamage();
        int enchants = s.getEnchantments().getSize();
        return tier * 10.0 + enchants + durability;
    }

    private int tierFor(ItemStack s) {
        String id = s.getItem().toString();
        if (id.contains("netherite")) return 5;
        if (id.contains("diamond"))   return 4;
        if (id.contains("iron"))      return 3;
        if (id.contains("chainmail")) return 2;
        if (id.contains("golden"))    return 1;
        if (id.contains("turtle"))    return 2;
        if (id.contains("leather"))   return 1;
        return 0;
    }
}
