package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/** Rolling build-speed estimate. Time is measured in active build ticks (20 per second). */
final class BuildForecast {
    private record Sample(int tick, int done) {}
    private static final int WINDOW_TICKS = 12000;
    private static final int MIN_SPAN_TICKS = 600;
    private final Deque<Sample> samples = new ArrayDeque<>();

    void reset() {
        this.samples.clear();
    }

    void record(int tick, int done) {
        Sample last = this.samples.peekLast();
        if (last != null && tick < last.tick()) this.samples.clear();
        else if (last != null && tick == last.tick()) this.samples.removeLast();
        this.samples.addLast(new Sample(tick, done));
        while (this.samples.size() > 2 && tick - this.samples.peekFirst().tick() > WINDOW_TICKS) this.samples.removeFirst();
    }

    /** Blocks per minute over the window, or -1 when there is not enough data or no progress. */
    double blocksPerMinute() {
        if (this.samples.size() < 2) return -1;
        Sample first = this.samples.peekFirst(), last = this.samples.peekLast();
        int span = last.tick() - first.tick(), gained = last.done() - first.done();
        if (span < MIN_SPAN_TICKS || gained <= 0) return -1;
        return gained * 1200.0 / span;
    }

    /** Whole minutes left, or -1 when unknown. */
    int etaMinutes(int done, int total) {
        double rate = this.blocksPerMinute();
        int left = total - done;
        if (rate <= 0 || left < 0) return -1;
        if (left == 0) return 0;
        return (int) Math.ceil(left / rate);
    }

    String line(int done, int total) {
        if (total <= 0) return null;
        double rate = this.blocksPerMinute();
        if (rate <= 0) return "Forecast: measuring build speed";
        int eta = this.etaMinutes(done, total);
        return String.format(Locale.ROOT, "Forecast: %.1f blocks/min | %s left", rate, duration(eta));
    }

    static String duration(int minutes) {
        if (minutes < 0) return "unknown time";
        if (minutes < 60) return minutes + "m";
        return minutes / 60 + "h" + String.format(Locale.ROOT, "%02d", minutes % 60) + "m";
    }
}
