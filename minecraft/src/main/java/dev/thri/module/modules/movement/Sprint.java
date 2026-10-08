package dev.thri.module.modules.movement;

import dev.thri.module.Category;
import dev.thri.module.Module;

public class Sprint extends Module {
    public Sprint() { super("Sprint", Category.MOVEMENT, 0); }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.player.forwardSpeed > 0 && !mc.player.isSneaking() && !mc.player.horizontalCollision)
            mc.player.setSprinting(true);
    }
}
