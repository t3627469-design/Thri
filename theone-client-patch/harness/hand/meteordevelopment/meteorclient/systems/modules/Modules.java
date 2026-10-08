package meteordevelopment.meteorclient.systems.modules;
public class Modules {
    private static final Modules INSTANCE = new Modules();
    public static Modules get() { return INSTANCE; }
    public boolean isActive(Class c) { return false; }
    public Module get(Class c) { return null; }
    public void add(Module m) { }
    public void save() { }
}
