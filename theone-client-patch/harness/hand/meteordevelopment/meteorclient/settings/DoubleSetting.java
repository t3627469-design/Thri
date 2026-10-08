package meteordevelopment.meteorclient.settings;
public class DoubleSetting extends Setting {
    public DoubleSetting(Object v) { super(v); }
    public Double get() { return (Double) value; }
}
