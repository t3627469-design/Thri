package meteordevelopment.meteorclient.settings;
import java.util.ArrayList;
import java.util.Iterator;
public class SettingGroup {
    public boolean sectionExpanded = true;
    private final ArrayList<Setting> settings = new ArrayList<>();
    public Setting add(Setting s) { settings.add(s); return s; }
    public Setting get(String name) { return null; }
    public Iterator iterator() { return settings.iterator(); }
}
