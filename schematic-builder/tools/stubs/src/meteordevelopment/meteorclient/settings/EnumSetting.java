package meteordevelopment.meteorclient.settings;
public class EnumSetting<T extends Enum<?>> extends Setting<T> {
    public static class Builder<T extends Enum<?>> extends SettingBuilder<Builder<T>, T, EnumSetting<T>> {
        public EnumSetting<T> build() { return null; }
    }
}
