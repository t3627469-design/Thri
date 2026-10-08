package dev.thri.module.modules.combat;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem extends Module {
    public AutoTotem() { super("AutoTotem", Category.COMBAT, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        ItemStack off = mc.player.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) return;
        int slot = findTotem();
        if (slot < 0) return;
        // offhand slot id = 45 in player screen handler
        mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, slot, 40, SlotActionType.SWAP, mc.player);
    }

    private int findTotem() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING))
                return i < 9 ? 36 + i : i;
        }
        return -1;
    }
}
