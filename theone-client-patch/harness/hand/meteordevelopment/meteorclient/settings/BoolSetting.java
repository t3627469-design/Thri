package meteordevelopment.meteorclient.settings;
public class BoolSetting extends Setting {
    public BoolSetting(Object v) { super(v); }
    public Boolean get() { return (Boolean) value; }
}
