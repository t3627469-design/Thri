package meteordevelopment.meteorclient.settings;
public class StringSetting$Builder {
    private Object def; private Object name; private double lo, hi;
    public StringSetting$Builder() { }
    public Object defaultValue(Object v) { def = v; return this; }
    public Object description(String s) { return this; }
    public Object name(String s) { name = s; return this; }
    public Object visible(Object v) { return this; }
    public StringSetting build() { return new StringSetting(def); }
}
