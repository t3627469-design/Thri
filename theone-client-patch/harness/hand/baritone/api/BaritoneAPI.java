package baritone.api;
public class BaritoneAPI {
    public static final Settings SETTINGS = new Settings();
    public static IBaritoneProvider getProvider() { return SimBaritone.PROVIDER; }
    public static Settings getSettings() { return SETTINGS; }
}
