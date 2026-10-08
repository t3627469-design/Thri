package dev.rex.farmbuilder.modules;

import net.minecraft.class_2338;

final class MiningWatch {
    private int progressAt;
    private int count;
    private int yieldAt;
    private class_2338 position;
    private final java.util.Set<class_2338> cleared = new java.util.LinkedHashSet<>();

    void start(int tick, class_2338 position, int count) {
        this.yieldAt = tick;
        this.cleared.clear();
        this.restart(tick, position, count);
    }

    void restart(int tick, class_2338 position, int count) {
        this.progressAt = tick;
        this.position = position;
        this.count = count;
    }

    boolean observe(int tick, class_2338 position, int count) {
        boolean collected = count > this.count;
        if (collected) this.yieldAt = tick;
        if (collected || this.position == null || position.method_10262(this.position) >= 4) {
            this.progressAt = tick;
            this.position = position;
        }
        this.count = count;
        return collected;
    }

    boolean stalled(int tick, int seconds) {
        return tick - this.progressAt > Math.max(5, seconds) * 20;
    }

    void blockCleared(int tick, class_2338 block) {
        if (this.cleared.add(block.method_10062())) {
            this.progressAt = tick;
            this.yieldAt = tick;
            if (this.cleared.size() > 2048) this.cleared.remove(this.cleared.iterator().next());
        }
    }

    boolean noYield(int tick, int seconds) {
        return tick - this.yieldAt > Math.max(120L, seconds * 3L) * 20L;
    }
}
