package dev.thri.module.modules.player;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class ChestStealer extends Module {
    public final Setting<Integer> delay = add(new Setting<>("Delay", 50, 0, 300));
    private long last;
    public ChestStealer() { super("ChestStealer", Category.PLAYER, 0); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h)) return;
        long now = System.currentTimeMillis();
        if (now - last < delay.get()) return;
        int rows = h.getRows();
        int size = rows * 9;
        for (int i = 0; i < size; i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (!s.isEmpty()) {
                mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                last = now;
                return;
            }
        }
        mc.player.closeHandledScreen();
    }
}
