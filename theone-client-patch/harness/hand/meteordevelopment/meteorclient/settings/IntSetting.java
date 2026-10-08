package meteordevelopment.meteorclient.settings;
public class IntSetting extends Setting {
    public IntSetting(Object v) { super(v); }
    public Integer get() { return (Integer) value; }
}
