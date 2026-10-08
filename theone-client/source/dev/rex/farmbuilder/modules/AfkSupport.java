package dev.rex.farmbuilder.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Settings;
import net.minecraft.class_2338;

final class AfkSupport {
    record Options(int headroom, int pickupMinutes, int lagSeconds, int chunkSeconds, boolean teleports) {}
    enum Chunk { OK, WAIT, REPLAN, STOP }
    private final Supplier<Options> options;
    private final Map<Integer, Long> pickups = new LinkedHashMap<>();
    private volatile long pulse;
    private long chunkAt;
    private int chunkRetries;
    private class_2338 position;

    AfkSupport(Settings settings) {
        var group = settings.createGroup("Overnight Recovery");
        var headroom = group.add(new IntSetting.Builder().name("inventory-headroom")
            .description("Clear surplus junk early, retaining this many free slots for ore and crafting.").defaultValue(4).range(3,9).build());
        var pickup = group.add(new IntSetting.Builder().name("failed-pickup-cooldown-minutes")
            .description("Leave an uncollectable item alone for this long after a failed pickup trip.").defaultValue(5).range(1,60).build());
        var lag = group.add(new IntSetting.Builder().name("server-lag-grace-seconds")
            .description("Pause after this long without server world updates. 0 disables. Saves and stops after two minutes without updates.")
            .defaultValue(10).range(0,60).build());
        var chunks = group.add(new IntSetting.Builder().name("chunk-load-grace-seconds")
            .description("Wait for the current movement's destination chunk before replanning. Three failed waits save and stop. 0 disables.")
            .defaultValue(15).range(0,60).build());
        var teleport = group.add(new BoolSetting.Builder().name("recover-unexpected-teleports")
            .description("Save and replan interrupted work after a sudden position jump. Intended home-return trips are excluded.").defaultValue(true).build());
        this.options = () -> new Options(headroom.get(),pickup.get(),lag.get(),chunks.get(),teleport.get());
    }

    AfkSupport(Supplier<Options> options) { this.options = options; }
    int headroom() { return this.options.get().headroom(); }
    void resetWorld() { this.pulse = 0; this.chunkAt = 0; this.chunkRetries = 0; this.position = null; this.pickups.clear(); }
    static boolean worldUpdate(Object packet) {
        return packet instanceof net.minecraft.class_2761 || packet instanceof net.minecraft.class_2626
            || packet instanceof net.minecraft.class_2672 || packet instanceof net.minecraft.class_2749;
    }
    void serverPulse(long now) { this.pulse = now; }
    boolean lagging(long now) { return this.options.get().lagSeconds() > 0 && this.pulse > 0 && now - this.pulse > this.options.get().lagSeconds() * 1000L; }
    boolean lagExpired(long now) { return this.lagging(now) && now - this.pulse >= 120000L; }
    void pickupFailed(int id, long now) {
        this.pickups.remove(id);
        this.pickups.put(id, now + this.options.get().pickupMinutes() * 60000L);
        while (this.pickups.size() > 256) this.pickups.remove(this.pickups.keySet().iterator().next());
    }
    boolean pickupBlocked(int id, long now) {
        this.pickups.values().removeIf(deadline -> deadline <= now);
        return this.pickups.containsKey(id);
    }
    Chunk chunk(long now, boolean loaded) {
        int seconds = this.options.get().chunkSeconds();
        if (loaded || seconds == 0) { this.chunkAt = 0; this.chunkRetries = 0; return Chunk.OK; }
        if (this.chunkAt == 0) this.chunkAt = now;
        if (now - this.chunkAt < seconds * 1000L) return Chunk.WAIT;
        this.chunkAt = now;
        return ++this.chunkRetries >= 3 ? Chunk.STOP : Chunk.REPLAN;
    }
    boolean teleported(class_2338 current, boolean working) {
        boolean changed = this.options.get().teleports() && working && this.position != null && current != null
            && current.method_10262(this.position) > 1024;
        this.position = current == null ? null : current.method_10062();
        return changed;
    }
}
