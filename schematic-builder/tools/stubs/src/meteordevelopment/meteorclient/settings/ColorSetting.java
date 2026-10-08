package meteordevelopment.meteorclient.settings;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
public class ColorSetting extends Setting<SettingColor> {
    public static class Builder extends SettingBuilder<Builder, SettingColor, ColorSetting> {
        public Builder defaultValue(SettingColor defaultValue) { return this; }
        public ColorSetting build() { return null; }
    }
}
