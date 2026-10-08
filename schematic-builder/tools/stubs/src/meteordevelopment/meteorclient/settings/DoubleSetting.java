package meteordevelopment.meteorclient.settings;
public class DoubleSetting extends Setting<Double> {
    public static class Builder extends SettingBuilder<Builder, Double, DoubleSetting> {
        public Builder defaultValue(double defaultValue) { return this; }
        public Builder range(double min, double max) { return this; }
        public Builder sliderRange(double min, double max) { return this; }
        public DoubleSetting build() { return null; }
    }
}
