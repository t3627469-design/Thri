package meteordevelopment.meteorclient.settings;
public class StringSetting extends Setting {
    public StringSetting(Object v) { super(v); }
    public String get() { return (String) value; }
}
