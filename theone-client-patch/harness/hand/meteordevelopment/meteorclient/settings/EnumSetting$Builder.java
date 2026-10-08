package meteordevelopment.meteorclient.settings;
public class EnumSetting$Builder {
    private Object def; private Object name; private double lo, hi;
    public EnumSetting$Builder() { }
    public Object defaultValue(Object v) { def = v; return this; }
    public Object description(String s) { return this; }
    public Object name(String s) { name = s; return this; }
    public EnumSetting build() { return new EnumSetting(def); }
}
