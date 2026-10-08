package meteordevelopment.meteorclient.settings;
public abstract class Setting<T> {
    public T get() { return null; }
    public boolean set(T value) { return true; }
    public abstract static class SettingBuilder<B, V, S> {
        public B name(String name) { return null; }
        public B description(String description) { return null; }
        public B defaultValue(V defaultValue) { return null; }
        public B visible(IVisible visible) { return null; }
        public abstract S build();
    }
}
