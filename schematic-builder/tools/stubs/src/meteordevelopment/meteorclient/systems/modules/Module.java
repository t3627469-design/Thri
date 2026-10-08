package meteordevelopment.meteorclient.systems.modules;
public abstract class Module {
    protected final net.minecraft.client.MinecraftClient mc = null;
    public final meteordevelopment.meteorclient.settings.Settings settings = null;
    public Module(Category category, String name, String desc) {}
    public void onActivate() {}
    public void onDeactivate() {}
    public void toggle() {}
    public boolean isActive() { return false; }
    public String getInfoString() { return null; }
    public void info(String message, Object... args) {}
    public void warning(String message, Object... args) {}
    public void error(String message, Object... args) {}
}
