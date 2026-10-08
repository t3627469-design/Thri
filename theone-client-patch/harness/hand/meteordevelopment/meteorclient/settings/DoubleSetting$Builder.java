package meteordevelopment.meteorclient.settings;
public class DoubleSetting$Builder {
    private Object def; private Object name; private double lo, hi;
    public DoubleSetting$Builder() { }
    public DoubleSetting$Builder min(double v) { return this; }
    public DoubleSetting$Builder sliderMax(double v) { return this; }
    public DoubleSetting$Builder range(double a, double b) { return this; }
    public DoubleSetting$Builder sliderRange(double a, double b) { return this; }
    public DoubleSetting$Builder defaultValue(double v) { def = v; return this; }
    public Object defaultValue(Object v) { def = v; return this; }
    public Object description(String s) { return this; }
    public Object name(String s) { name = s; return this; }
    public Object visible(Object v) { return this; }
    public DoubleSetting build() { return new DoubleSetting(def); }
}
