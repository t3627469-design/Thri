package dev.thri.module.modules.combat;

import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;

public class Velocity extends Module {
    public final Setting<Boolean> kb        = add(new Setting<>("Knockback", true));
    public final Setting<Boolean> explosion = add(new Setting<>("Explosion", true));
    public Velocity() { super("Velocity", Category.COMBAT, 0); }
    public boolean cancelKnockback() { return kb.get(); }
    public boolean cancelExplosion() { return explosion.get(); }
}
