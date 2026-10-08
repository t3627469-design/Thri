package dev.thri.gui;

import dev.thri.Thri;
import dev.thri.module.Category;
import dev.thri.module.Module;
import dev.thri.module.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.*;

public class ClickGuiScreen extends Screen {
    private static final int PANEL_W = 110, HEADER_H = 14, ITEM_H = 13;
    private static final int BG = 0xDD12131A, HDR = 0xFF00D4AA, ITEM = 0xFF1C1E29, ON = 0xFF00F0C0, OFF = 0xFFAAAAAA, SUB = 0xFF2A2D3B;

    private static final Map<Category, int[]> POS = new HashMap<>();
    private final Set<Module> expanded = new HashSet<>();
    private Module dragging; private int dragDx, dragDy;
    private int catOffsetX, catOffsetY; private Category draggingCat;

    public ClickGuiScreen() {
        super(Text.literal("Thri"));
        int x = 10, y = 10;
        for (Category c : Category.values()) POS.computeIfAbsent(c, k -> new int[]{10 + c.ordinal() * (PANEL_W + 6), 10});
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float dt) {
        this.renderBackground(ctx, mx, my, dt);
        Map<Category, List<Module>> by = new EnumMap<>(Category.class);
        for (Module m : Thri.MODULES.all()) by.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);

        for (Category c : Category.values()) {
            int[] p = POS.get(c);
            int x = p[0], y = p[1];
            ctx.fill(x, y, x + PANEL_W, y + HEADER_H, HDR);
            ctx.drawTextWithShadow(textRenderer, Text.literal(c.name()), x + 4, y + 3, 0xFF101018);
            int yy = y + HEADER_H;
            List<Module> ms = by.getOrDefault(c, List.of());
            for (Module m : ms) {
                ctx.fill(x, yy, x + PANEL_W, yy + ITEM_H, ITEM);
                ctx.drawTextWithShadow(textRenderer, Text.literal(m.name), x + 4, yy + 3, m.isEnabled() ? ON : OFF);
                if (!m.settings.isEmpty()) ctx.drawTextWithShadow(textRenderer, Text.literal(expanded.contains(m) ? "−" : "+"), x + PANEL_W - 10, yy + 3, OFF);
                yy += ITEM_H;
                if (expanded.contains(m)) {
                    for (Setting<?> s : m.settings) {
                        ctx.fill(x, yy, x + PANEL_W, yy + ITEM_H, SUB);
                        String txt = s.name + ": " + s.get();
                        ctx.drawTextWithShadow(textRenderer, Text.literal(txt), x + 8, yy + 3, 0xFFCFCFCF);
                        yy += ITEM_H;
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Map<Category, List<Module>> by = new EnumMap<>(Category.class);
        for (Module m : Thri.MODULES.all()) by.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);
        for (Category c : Category.values()) {
            int[] p = POS.get(c);
            int x = p[0], y = p[1];
            if (mx >= x && mx <= x + PANEL_W && my >= y && my <= y + HEADER_H) {
                draggingCat = c; catOffsetX = (int) mx - x; catOffsetY = (int) my - y;
                return true;
            }
            int yy = y + HEADER_H;
            for (Module m : by.getOrDefault(c, List.of())) {
                if (mx >= x && mx <= x + PANEL_W && my >= yy && my <= yy + ITEM_H) {
                    if (button == 0) m.toggle();
                    else if (button == 1 && !m.settings.isEmpty()) {
                        if (!expanded.add(m)) expanded.remove(m);
                    }
                    return true;
                }
                yy += ITEM_H;
                if (expanded.contains(m)) {
                    for (Setting<?> s : m.settings) {
                        if (mx >= x && mx <= x + PANEL_W && my >= yy && my <= yy + ITEM_H) {
                            cycleSetting(s);
                            return true;
                        }
                        yy += ITEM_H;
                    }
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingCat != null) {
            int[] p = POS.get(draggingCat);
            p[0] = (int) mx - catOffsetX;
            p[1] = (int) my - catOffsetY;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        draggingCat = null;
        return super.mouseReleased(mx, my, button);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cycleSetting(Setting s) {
        Object v = s.get();
        if (v instanceof Boolean b) s.set(!b);
        else if (v instanceof Integer i && s.max != null) {
            int next = i + 1; if (next > ((Integer) s.max)) next = (Integer) s.min;
            s.set(next);
        }
        else if (v instanceof Double d && s.max != null) {
            double step = (((Double) s.max) - ((Double) s.min)) / 10.0;
            double next = d + step; if (next > (Double) s.max) next = (Double) s.min;
            s.set(Math.round(next * 100.0) / 100.0);
        }
        else if (v instanceof Enum<?> e) {
            Object[] vals = e.getClass().getEnumConstants();
            s.set(vals[(e.ordinal() + 1) % vals.length]);
        }
    }

    @Override public boolean shouldPause() { return false; }
}
