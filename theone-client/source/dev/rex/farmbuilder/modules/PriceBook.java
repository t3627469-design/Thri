package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Remembers the unit prices seen on the auction house so absurd listings can be skipped. */
final class PriceBook {
    static final int MIN_SAMPLES = 6;
    private static final int MAX_SAMPLES = 40;
    private static final int MAX_ITEMS = 512;
    private final Map<String, Deque<Double>> prices = new HashMap<>();
    private int changes;

    void record(String item, double unit) {
        if (item == null || item.isEmpty() || !Double.isFinite(unit) || unit <= 0.0) return;
        if (!this.prices.containsKey(item) && this.prices.size() >= MAX_ITEMS) return;
        Deque<Double> d = this.prices.computeIfAbsent(item, k -> new ArrayDeque<>());
        d.addLast(unit);
        while (d.size() > MAX_SAMPLES) d.removeFirst();
        this.changes++;
    }

    /** Median unit price, or -1 until enough listings have been seen. */
    double fair(String item) {
        Deque<Double> d = this.prices.get(item);
        if (d == null || d.size() < MIN_SAMPLES) return -1;
        List<Double> sorted = new ArrayList<>(d);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    boolean tooExpensive(String item, double unit, double factor) {
        double fair = this.fair(item);
        return factor > 0 && fair > 0 && unit > fair * factor;
    }

    int size() {
        return this.prices.size();
    }

    /** True once enough new samples exist that saving is worthwhile; resets the counter. */
    boolean takeDirty() {
        if (this.changes < 10) return false;
        this.changes = 0;
        return true;
    }

    String serialize() {
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(this.prices).forEach((item, d) -> {
            sb.append(item).append('=');
            boolean first = true;
            for (double v : d) {
                if (!first) sb.append(',');
                sb.append(String.format(Locale.ROOT, "%.4f", v));
                first = false;
            }
            sb.append('\n');
        });
        return sb.toString();
    }

    void load(String text) {
        this.prices.clear();
        this.changes = 0;
        if (text == null) return;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0 || this.prices.size() >= MAX_ITEMS) continue;
            String item = line.substring(0, eq).trim();
            for (String part : line.substring(eq + 1).split(",")) {
                try { this.record(item, Double.parseDouble(part.trim())); }
                catch (NumberFormatException ignored) { }
            }
        }
        this.changes = 0;
    }
}
