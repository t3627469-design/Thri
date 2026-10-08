package dev.rex.farmbuilder.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_327;
import net.minecraft.class_332;

/**
 * The TheOne Client dashboard: a dark glass card with a cyan-to-violet accent, a live state pill,
 * the current activity, segmented progress bars, detail rows and stat chips.
 * Drawn with plain fills and text so it works on any GUI scale; layout is redone only when the model changes.
 */
final class HudPanel {
   static final int W = 172;
   private static final int PAD = 6;
   private static final int LH = 10;
   private static final int HEAD = 38;
   private static final int FOOT = 15;

   private static final int SHADOW = 0x40000000;
   private static final int BG = 0xF00A0B12;
   private static final int HEAD_TOP = 0xFF151826;
   private static final int CARD = 0xFF12141F;
   private static final int TRACK = 0xFF1B1E2D;
   private static final int CHIP = 0xFF151826;
   private static final int TEXT = 0xFFE8ECF6;
   private static final int MUTED = 0xFF8A93AD;
   private static final int DIM = 0xFF565E78;
   static final int ACCENT = 0xFF00E0FF;
   static final int VIOLET = 0xFF9B6BFF;
   // Legacy colour names kept so older callers still compile; ORANGE is drawn as the accent.
   static final int ORANGE = 0xFFFF7518;
   static final int RED = 0xFFFF5266;
   static final int AMBER = 0xFFFFB454;
   static final int PURPLE = 0xFFB28CFF;
   static final int GREEN = 0xFF3CF0A0;
   static final int BLUE = 0xFF5AA8FF;
   static final int TEAL = 0xFF2DE0D0;
   static final int YELLOW = 0xFFFFD25A;
   static final int GREY = 0xFF7A8098;

   static final class Bar {
      final String label;
      final String value;
      final float frac;
      final int color;

      Bar(String label, String value, float frac, int color) {
         this.label = label;
         this.value = value;
         this.frac = Math.max(0.0F, Math.min(1.0F, frac));
         this.color = color;
      }
   }

   static final class Model {
      String title = "FARM BUILDER";
      String subtitle = "";
      String badge = "IDLE";
      int badgeColor = GREY;
      String activity = "";
      final List<Bar> bars = new ArrayList<>();
      final List<String[]> rows = new ArrayList<>();
      final List<String> chips = new ArrayList<>();

      void bar(String label, String value, float frac, int color) {
         this.bars.add(new Bar(label, value, frac, color));
      }

      void row(String label, String value) {
         if (value != null && !value.isBlank()) this.rows.add(new String[]{label, value});
      }

      void chip(String text) {
         this.chips.add(text);
      }
   }

   private final Map<String, Float> shown = new HashMap<>();
   private long lastFrame;
   private Model laidOut;
   private boolean laidCompact;
   private String title;
   private String subtitle;
   private String badge;
   private int badgeW;
   private List<String> activity = List.of();
   private final List<String[]> rows = new ArrayList<>();
   private int labelW;
   private final List<int[]> chipPos = new ArrayList<>();
   private final List<String> chipText = new ArrayList<>();
   private int chipRows;
   private List<Bar> bars = List.of();
   private int height;

   static int badgeColor(String badge) {
      return switch (badge) {
         case "BUILDING" -> ACCENT;
         case "GATHERING", "DONE" -> GREEN;
         case "SHOPPING" -> TEAL;
         case "STORAGE", "PLANNING", "CLEANUP" -> VIOLET;
         case "TRAVEL", "LOADING" -> BLUE;
         case "COMBAT", "ERROR" -> RED;
         case "SURVIVAL" -> 0xFFFF7A59;
         case "PAUSED", "UNSTUCK", "WAITING" -> YELLOW;
         default -> GREY;
      };
   }

   /** Callers that were built with the old orange accent get the new one. */
   private static int tone(int c) {
      return c == ORANGE ? ACCENT : c;
   }

   void render(class_332 g, class_327 font, int x, int y, Model m, boolean compact) {
      if (m != this.laidOut || compact != this.laidCompact) this.layout(font, m, compact);
      long now = System.currentTimeMillis();
      float dt = this.lastFrame == 0 ? 1.0F : Math.min(1.0F, (now - this.lastFrame) / 1000.0F);
      this.lastFrame = now;
      int r = x + W;
      int b = y + this.height;
      int bc = tone(this.laidOut.badgeColor);

      // Soft shadow, then a 1px frame that fades from cyan to violet down the card, then the glass body.
      round(g, x + 1, y + 2, r + 1, b + 3, SHADOW);
      round(g, x + 2, y + 4, r + 2, b + 4, 0x22000000);
      for (int i = 0; i < this.height; i++) {
         int c = withAlpha(mix(ACCENT, VIOLET, i / (float) this.height), 0xB0);
         int inset = i == 0 || i == this.height - 1 ? 1 : 0;
         g.method_25294(x + inset, y + i, r - inset, y + i + 1, c);
      }
      round(g, x + 1, y + 1, r - 1, b - 1, BG);
      for (int i = 0; i < HEAD - 2; i++) {
         int c = mix(HEAD_TOP, BG, i / (float) (HEAD - 2));
         int inset = i == 0 ? 2 : 1;
         g.method_25294(x + inset, y + 1 + i, r - inset, y + 2 + i, c);
      }

      // Header: gradient logo tile, brand line, title (drawn twice for weight) and subtitle.
      int ix = x + PAD;
      int iy = y + 7;
      for (int i = 0; i < 16; i++) g.method_25294(ix + (i == 0 || i == 15 ? 1 : 0), iy + i, ix + 16 - (i == 0 || i == 15 ? 1 : 0), iy + i + 1, mix(ACCENT, VIOLET, i / 15.0F));
      g.method_25294(ix + 2, iy + 2, ix + 14, iy + 3, 0x50FFFFFF);
      g.method_25303(font, "1", ix + 5, iy + 4, 0xFF081018);
      int tx = ix + 22;
      g.method_25303(font, "THEONE CLIENT", tx, y + 6, ACCENT);
      g.method_25303(font, this.title, tx, y + 17, TEXT);
      g.method_25303(font, this.title, tx + 1, y + 17, TEXT);
      g.method_25303(font, this.subtitle, tx, y + 27, MUTED);

      // Live state pill with a pulsing dot.
      int bx = r - PAD - this.badgeW;
      int by = y + 6;
      round(g, bx, by, r - PAD, by + 12, withAlpha(bc, 0x28));
      outline(g, bx, by, r - PAD, by + 12, withAlpha(bc, 0xA0));
      float pulse = 0.5F + 0.5F * (float) Math.sin(now / 280.0);
      round(g, bx + 4, by + 4, bx + 8, by + 8, withAlpha(bc, 0x60 + (int) (pulse * 0x9F)));
      g.method_25303(font, this.badge, bx + 11, by + 2, bc);

      // Gradient divider with a faint glow below it.
      int dy = y + HEAD;
      for (int px = 1; px < W - 1; px++) g.method_25294(x + px, dy, x + px + 1, dy + 1, mix(ACCENT, VIOLET, px / (float) W));
      g.method_25294(x + 1, dy + 1, r - 1, dy + 3, 0x120EE0FF);

      // What it is doing right now, with a stripe in the state colour.
      int cy = dy + 6;
      int ch = 6 + this.activity.size() * LH;
      round(g, x + PAD, cy, r - PAD, cy + ch, CARD);
      g.method_25294(x + PAD, cy + 1, x + PAD + 2, cy + ch - 1, bc);
      for (int i = 0; i < this.activity.size(); i++) {
         g.method_25303(font, this.activity.get(i), x + PAD + 7, cy + 3 + i * LH, TEXT);
      }
      int yy = cy + ch;

      // Progress bars ease toward their targets, are cut into ten segments and shimmer while filling.
      if (!this.bars.isEmpty()) {
         yy += 7;
         int bl = x + PAD;
         int br = r - PAD;
         for (Bar bar : this.bars) {
            float cur = this.shown.getOrDefault(bar.label, bar.frac);
            cur += (bar.frac - cur) * (1.0F - (float) Math.exp(-dt * 7.0F));
            this.shown.put(bar.label, cur);
            int col = tone(bar.color);
            g.method_25303(font, bar.label, bl, yy, MUTED);
            g.method_25303(font, bar.value, br - font.method_1727(bar.value), yy, TEXT);
            int ty = yy + LH;
            round(g, bl, ty, br, ty + 5, TRACK);
            int fw = Math.round((br - bl) * cur);
            if (fw >= 2) {
               int end = col == ACCENT ? VIOLET : mix(col, 0xFFFFFFFF, 0.35F);
               for (int px = 0; px < fw; px++) {
                  int c = mix(col, end, px / (float) Math.max(1, br - bl));
                  int inset = (px == 0 || px == fw - 1) ? 1 : 0;
                  g.method_25294(bl + px, ty + inset, bl + px + 1, ty + 5 - inset, c);
               }
               g.method_25294(bl + 1, ty + 1, bl + fw - 1, ty + 2, 0x40FFFFFF);
               if (cur < 0.999F && fw > 12) {
                  int span = fw + 16;
                  int sx = bl - 8 + (int) ((now / 14L) % span);
                  int s0 = Math.max(bl + 1, sx);
                  int s1 = Math.min(bl + fw - 1, sx + 8);
                  if (s1 > s0) g.method_25294(s0, ty + 1, s1, ty + 4, 0x30FFFFFF);
               }
            }
            for (int s = 1; s < 10; s++) {
               int sx = bl + (br - bl) * s / 10;
               g.method_25294(sx, ty, sx + 1, ty + 5, 0x55000000);
            }
            yy = ty + 8;
         }
         yy -= 2;
      }

      // Detail rows: a muted label column, bright values and hairline separators.
      if (!this.rows.isEmpty()) {
         yy += 4;
         g.method_25294(x + PAD, yy, r - PAD, yy + 1, 0x22FFFFFF);
         yy += 5;
         for (String[] row : this.rows) {
            g.method_25303(font, row[0], x + PAD, yy, DIM);
            g.method_25303(font, row[1], x + PAD + this.labelW + 7, yy, TEXT);
            yy += LH;
         }
      }

      // Stat chips: dark pills with a hairline accent outline.
      if (!this.chipText.isEmpty()) {
         yy += 4;
         for (int i = 0; i < this.chipText.size(); i++) {
            int[] p = this.chipPos.get(i);
            int cx = x + p[0];
            int cyy = yy + p[1];
            round(g, cx, cyy, cx + p[2], cyy + 11, CHIP);
            outline(g, cx, cyy, cx + p[2], cyy + 11, withAlpha(ACCENT, 0x30));
            g.method_25303(font, this.chipText.get(i), cx + 3, cyy + 2, MUTED);
         }
         yy += this.chipRows * 13 - 2;
      }

      // Footer brand line.
      g.method_25294(x + PAD, b - FOOT, r - PAD, b - FOOT + 1, 0x16FFFFFF);
      g.method_25303(font, "◆ TheOne", x + PAD, b - FOOT + 5, DIM);
   }

   private void layout(class_327 font, Model m, boolean compact) {
      this.laidOut = m;
      this.laidCompact = compact;
      int inner = W - 2 * PAD;
      this.badge = m.badge == null ? "IDLE" : m.badge;
      this.badgeW = font.method_1727(this.badge) + 15;
      this.title = fit(font, m.title, inner - 22);
      this.subtitle = fit(font, m.subtitle, inner - 22);
      this.activity = wrap(font, m.activity == null || m.activity.isBlank() ? "Idle" : m.activity, inner - 10, compact ? 1 : 2);
      this.bars = compact && m.bars.size() > 1 ? m.bars.subList(0, 1) : m.bars;

      this.rows.clear();
      this.labelW = 0;
      this.chipText.clear();
      this.chipPos.clear();
      this.chipRows = 0;
      if (!compact) {
         for (String[] row : m.rows) this.labelW = Math.max(this.labelW, font.method_1727(row[0]));
         for (String[] row : m.rows) this.rows.add(new String[]{row[0], fit(font, row[1], inner - this.labelW - 7)});
         int cx = 0;
         int line = 0;
         for (String c : m.chips) {
            String t = fit(font, c, inner - 8);
            int cw = font.method_1727(t) + 6;
            if (cx > 0 && cx + cw > inner) {
               cx = 0;
               line++;
            }
            this.chipText.add(t);
            this.chipPos.add(new int[]{PAD + cx, line * 13, cw});
            cx += cw + 3;
         }
         this.chipRows = m.chips.isEmpty() ? 0 : line + 1;
      }

      int h = HEAD + 6 + 6 + this.activity.size() * LH;
      if (!this.bars.isEmpty()) h += 5 + this.bars.size() * (LH + 8);
      if (!this.rows.isEmpty()) h += 9 + this.rows.size() * LH;
      if (this.chipRows > 0) h += 4 + this.chipRows * 13 - 2;
      this.height = h + FOOT + 3;
   }

   static String fit(class_327 font, String s, int max) {
      if (s == null) return "";
      if (font.method_1727(s) <= max) return s;
      int lo = 0;
      int hi = s.length();
      while (lo < hi) {
         int mid = (lo + hi + 1) / 2;
         if (font.method_1727(s.substring(0, mid) + "...") <= max) lo = mid;
         else hi = mid - 1;
      }
      return s.substring(0, lo).stripTrailing() + "...";
   }

   static List<String> wrap(class_327 font, String s, int max, int maxLines) {
      List<String> out = new ArrayList<>();
      StringBuilder line = new StringBuilder();
      String[] words = s.split(" ");
      for (int i = 0; i < words.length; i++) {
         String w = words[i];
         String next = line.length() == 0 ? w : line + " " + w;
         if (font.method_1727(next) <= max || line.length() == 0) {
            line.setLength(0);
            line.append(next);
         } else {
            if (out.size() == maxLines - 1) {
               StringBuilder rest = new StringBuilder(line);
               for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
               out.add(fit(font, rest.toString(), max));
               return out;
            }
            out.add(line.toString());
            line.setLength(0);
            line.append(w);
         }
      }
      if (line.length() > 0) out.add(fit(font, line.toString(), max));
      return out;
   }

   /** Rectangle with its four corner pixels cut, which reads as rounded at GUI scale. */
   private static void round(class_332 g, int x0, int y0, int x1, int y1, int c) {
      if (x1 - x0 < 3 || y1 - y0 < 3) {
         g.method_25294(x0, y0, x1, y1, c);
         return;
      }
      g.method_25294(x0 + 1, y0, x1 - 1, y0 + 1, c);
      g.method_25294(x0, y0 + 1, x1, y1 - 1, c);
      g.method_25294(x0 + 1, y1 - 1, x1 - 1, y1, c);
   }

   private static void outline(class_332 g, int x0, int y0, int x1, int y1, int c) {
      g.method_25294(x0 + 1, y0, x1 - 1, y0 + 1, c);
      g.method_25294(x0 + 1, y1 - 1, x1 - 1, y1, c);
      g.method_25294(x0, y0 + 1, x0 + 1, y1 - 1, c);
      g.method_25294(x1 - 1, y0 + 1, x1, y1 - 1, c);
   }

   static int withAlpha(int c, int a) {
      return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
   }

   static int mix(int a, int b, float t) {
      int r = 0;
      for (int s = 0; s <= 24; s += 8) {
         int ca = a >>> s & 0xFF;
         int cb = b >>> s & 0xFF;
         r |= Math.round(ca + (cb - ca) * t) << s;
      }
      return r;
   }
}
