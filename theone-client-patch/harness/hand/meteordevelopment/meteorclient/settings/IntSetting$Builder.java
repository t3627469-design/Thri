package meteordevelopment.meteorclient.settings;
public class IntSetting$Builder {
    private Object def; private Object name; private double lo, hi;
    public IntSetting$Builder() { }
    public IntSetting$Builder min(int v) { return this; }
    public IntSetting$Builder sliderMax(int v) { return this; }
    public IntSetting$Builder range(int a, int b) { return this; }
    public IntSetting$Builder sliderRange(int a, int b) { return this; }
    public Object defaultValue(Object v) { def = v; return this; }
    public Object description(String s) { return this; }
    public Object name(String s) { name = s; return this; }
    public Object visible(Object v) { return this; }
    public IntSetting build() { return new IntSetting(def); }
}
