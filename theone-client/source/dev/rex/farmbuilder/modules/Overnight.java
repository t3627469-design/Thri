package dev.rex.farmbuilder.modules;

import java.nio.file.Path;
import java.time.Instant;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.Settings;

final class Overnight {
    record Options(boolean waitForRoom, int waitMinutes, int foodReserve, int reportMinutes, int sessionHours) {}
    private final java.util.function.Supplier<Options> options;
    private long started;
    private long nextReport;
    private long inventoryWait;
    private String hold = "";

    Overnight(Settings settings) {
        SettingGroup group = settings.createGroup("Overnight");
        var waitForRoom = group.add(new BoolSetting.Builder().name("wait-for-inventory-room")
            .description("Pause with a saved job when required items fill inventory; resume when slots are freed.").defaultValue(true).build());
        var waitMinutes = group.add(new IntSetting.Builder().name("inventory-wait-minutes")
            .description("Save and stop after this long waiting for inventory space. 0 waits indefinitely.")
            .defaultValue(30).range(0,720).sliderRange(0,120).build());
        var foodReserve = group.add(new IntSetting.Builder().name("food-reserve-items")
            .description("Hunt before running out of ordinary food. 0 disables the reserve; hunt-for-food must be enabled.")
            .defaultValue(8).range(0,64).sliderRange(0,32).build());
        var reportMinutes = group.add(new IntSetting.Builder().name("heartbeat-minutes")
            .description("Write an overnight status snapshot, including while paused or disconnected. 0 disables.")
            .defaultValue(1).range(0,60).sliderRange(0,10).build());
        var sessionHours = group.add(new IntSetting.Builder().name("session-limit-hours")
            .description("Save and stop after this many real hours in this activation, including menus and reconnects. 0 disables.")
            .defaultValue(0).range(0,72).sliderRange(0,12).build());
        this.options = () -> new Options(waitForRoom.get(), waitMinutes.get(), foodReserve.get(), reportMinutes.get(), sessionHours.get());
    }

    Overnight(java.util.function.Supplier<Options> options) { this.options = java.util.Objects.requireNonNull(options); }

    void start(long now) { started = now; nextReport = now; inventoryWait = 0; hold = ""; }
    int foodReserve() { return options.get().foodReserve(); }
    String hold() { return hold; }
    boolean expired(long now) { return expired(started, now, options.get().sessionHours(), 3600000L); }
    boolean inventoryExpired(long now) { return inventoryWait > 0 && expired(inventoryWait, now, options.get().waitMinutes(), 60000L); }
    boolean waitEnabled() { return options.get().waitForRoom(); }

    void waitInventory(long now) { if (inventoryWait == 0) inventoryWait = now; hold = "Waiting for three free inventory slots"; }
    void clearInventoryWait() { inventoryWait = 0; hold = ""; }
    boolean waitingInventory() { return inventoryWait != 0; }

    static boolean expired(long start, long now, int units, long unitMillis) {
        return units > 0 && start > 0 && now >= start && now - start >= (long) units * unitMillis;
    }

    boolean reportDue(long now) {
        if (options.get().reportMinutes() == 0 || now < nextReport) return false;
        nextReport = now + options.get().reportMinutes() * 60000L;
        return true;
    }

    void report(Path path, long now, String activity, int reconnects, int food, int slots, String mine) throws Exception {
        FarmFiles.writeAtomic(path, "Farm Builder overnight status\nUpdated: " + Instant.ofEpochMilli(now)
            + "\nSession minutes: " + (started > 0 ? Math.max(0,now-started)/60000L : 0) + "\nActivity: " + activity
            + "\nHold: " + (hold.isEmpty() ? "none" : hold) + "\nReconnect attempts: " + reconnects
            + "\nOrdinary food items: " + food + "\nFree inventory slots: " + slots
            + "\nMining: " + mine + "\nSession limit hours: " + options.get().sessionHours() + "\n");
    }
}
