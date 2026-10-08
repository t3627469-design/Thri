package meteordevelopment.meteorclient.settings;
public class IntSetting extends Setting<Integer> {
    public static class Builder extends SettingBuilder<Builder, Integer, IntSetting> {
        public Builder range(int min, int max) { return this; }
        public Builder sliderRange(int min, int max) { return this; }
        public Builder noSlider() { return this; }
        public IntSetting build() { return null; }
    }
}
