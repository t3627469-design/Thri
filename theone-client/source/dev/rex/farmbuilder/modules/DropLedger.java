package dev.rex.farmbuilder.modules;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import net.minecraft.class_1792;
import net.minecraft.class_2338;

final class DropLedger {
    private record Drop(class_1792 item, class_2338 position, long expires) {}
    private final Deque<Drop> dropped = new ArrayDeque<>();
    private String world;
    private int trips;

    void world(String world) {
        if (!Objects.equals(this.world, world)) { this.dropped.clear(); this.trips = 0; this.world = world; }
    }
    void discarded(class_1792 item, class_2338 at, long now) {
        this.dropped.addLast(new Drop(item, at.method_10062(), now + 600000L));
        while (this.dropped.size() > 128) this.dropped.removeFirst();
    }
    boolean ignored(class_1792 item, class_2338 at, long now) {
        this.dropped.removeIf(drop -> drop.expires() <= now);
        return this.dropped.stream().anyMatch(drop -> drop.item() == item && drop.position().method_10262(at) <= 49);
    }
    class_2338 nextSpot(class_2338 origin) {
        int trip = this.trips++;
        return origin.method_10069(-12 - (trip % 4) * 5, 0, -12 - (trip / 4 % 4) * 5);
    }
    boolean crowded(class_2338 at, long now) {
        this.dropped.removeIf(drop -> drop.expires() <= now);
        return this.dropped.stream().anyMatch(drop -> drop.position().method_10262(at) <= 49);
    }
}
