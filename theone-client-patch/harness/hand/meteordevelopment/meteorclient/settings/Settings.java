package meteordevelopment.meteorclient.settings;
public class Settings {
    private final SettingGroup def = new SettingGroup();
    public SettingGroup getDefaultGroup() { return def; }
    public SettingGroup createGroup(String name) { return new SettingGroup(); }
    public Setting get(String name) { return null; }
    public java.util.Iterator iterator() { return null; }
}
