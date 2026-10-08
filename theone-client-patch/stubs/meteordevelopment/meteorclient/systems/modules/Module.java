package meteordevelopment.meteorclient.systems.modules;
public abstract class Module {
    public Module(Category c, String name, String description) { }
    public boolean isActive() { return false; }
    public void toggle() { }
    public void onActivate() { }
    public void onDeactivate() { }
    public void info(String fmt, Object... args) { }
    public void warning(String fmt, Object... args) { }
    public void error(String fmt, Object... args) { }
}
