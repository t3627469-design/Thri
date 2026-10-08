package meteordevelopment.meteorclient.systems.modules;
import java.util.ArrayList;
import java.util.List;
public class Modules {
    private static final Modules INSTANCE = new Modules();
    private final List<Module> all = new ArrayList<>();
    public static Modules get() { return INSTANCE; }
    public boolean isActive(Class c) { return false; }
    public Module get(Class c) { for (Module m : all) if (m.getClass() == c) return m; return null; }
    public void add(Module m) { all.add(m); }
    public void save() { }
}
