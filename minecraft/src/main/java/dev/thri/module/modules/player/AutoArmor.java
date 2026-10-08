package dev.thri.module.modules.player;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ArmorItem;
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
                if (!(s.getItem() instanceof ArmorItem ai)) continue;
                if (ai.getSlotType() != slot) continue;
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
        if (!(s.getItem() instanceof ArmorItem ai)) return -1;
        if (ai.getSlotType() != slot) return -1;
        int tier = switch (ai.getMaterial().getIdAsString()) {
            case "minecraft:netherite" -> 5;
            case "minecraft:diamond"   -> 4;
            case "minecraft:iron"      -> 3;
            case "minecraft:chainmail" -> 2;
            case "minecraft:gold"      -> 1;
            default                    -> 0;
        };
        double durability = s.getMaxDamage() == 0 ? 1 : 1.0 - (double) s.getDamage() / s.getMaxDamage();
        int enchants = s.getEnchantments().getSize();
        return tier * 10.0 + enchants + durability;
    }
}
