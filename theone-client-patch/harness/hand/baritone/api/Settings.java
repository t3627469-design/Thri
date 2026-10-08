package baritone.api;
public class Settings {
    public static class Setting { public Object value; public Setting(Object v) { value = v; } }
    public Setting blockReachDistance = new Setting(4.5f);
    public Setting allowBreak = new Setting(true);
    public Setting allowPlace = new Setting(true);
    public Setting allowParkourPlace = new Setting(true);
    public Setting allowBreakAnyway = new Setting(new java.util.ArrayList<Object>());
}
