package meteordevelopment.meteorclient.systems.modules;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.Settings;
public abstract class Module {
    public final String name;
    public final Settings settings = new Settings();
    public final net.minecraft.class_310 mc = MeteorClient.mc;
    public Module(Category c, String name, String description) { this.name = name; }
    private boolean active;
    public boolean isActive() { return active; }
    public void toggle() { active = !active; if (active) onActivate(); else onDeactivate(); }
    public void onActivate() { }
    public void onDeactivate() { }
    public void info(String fmt, Object... args) { System.out.println("[info] " + String.format(fmt, args)); }
    public void warning(String fmt, Object... args) { System.out.println("[WARN] " + String.format(fmt, args)); }
    public void error(String fmt, Object... args) { System.out.println("[ERROR] " + String.format(fmt, args)); }
}
