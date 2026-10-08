package meteordevelopment.meteorclient.settings;
public class Setting {
    public String name;
    protected Object value;
    public Setting() { }
    public Setting(Object v) { value = v; }
    public Object get() { return value; }
    public boolean set(Object v) { value = v; return true; }
    public boolean parse(String s) { return false; }
}
