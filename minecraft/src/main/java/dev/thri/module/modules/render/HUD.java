package dev.thri.module.modules.render;

import dev.thri.Thri;
import dev.thri.module.Category;
import dev.thri.module.Module;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;

public class HUD extends Module {
    public HUD() {
        super("HUD", Category.RENDER, 0);
        HudRenderCallback.EVENT.register(this::render);
        setEnabled(true);
    }

    private void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tc) {
        if (!isEnabled() || Thri.MODULES == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        ctx.drawTextWithShadow(tr, Text.literal("§bThri"), 4, 4, 0xFF00F0C0);
        int y = 16;
        List<dev.thri.module.Module> ms = Thri.MODULES.all();
        for (dev.thri.module.Module m : ms) {
            if (!m.isEnabled() || m == this) continue;
            String s = "§f" + m.name;
            int w = tr.getWidth(s);
            ctx.fill(ctx.getScaledWindowWidth() - w - 6, y, ctx.getScaledWindowWidth(), y + 10, 0x80000000);
            ctx.drawTextWithShadow(tr, Text.literal(s), ctx.getScaledWindowWidth() - w - 3, y + 1, 0xFF00F0C0);
            y += 11;
        }
    }
}
