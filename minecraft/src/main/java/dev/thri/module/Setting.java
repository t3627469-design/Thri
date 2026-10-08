package dev.thri.module;

public class Setting<T> {
    public final String name;
    private T value;
    public final T min, max;
    public Setting(String name, T def) { this(name, def, null, null); }
    public Setting(String name, T def, T min, T max) {
        this.name = name; this.value = def; this.min = min; this.max = max;
    }
    public T get() { return value; }
    public void set(T v) { this.value = v; }

    @SuppressWarnings("unchecked")
    public void setFromObject(Object o) {
        if (value instanceof Boolean) this.value = (T) Boolean.valueOf(o.toString());
        else if (value instanceof Integer) this.value = (T) Integer.valueOf(((Number) o).intValue());
        else if (value instanceof Double) this.value = (T) Double.valueOf(((Number) o).doubleValue());
        else if (value instanceof Float) this.value = (T) Float.valueOf(((Number) o).floatValue());
        else if (value instanceof String) this.value = (T) o.toString();
        else if (value instanceof Enum<?>) {
            Enum<?> e = (Enum<?>) value;
            for (Object c : e.getClass().getEnumConstants())
                if (((Enum<?>) c).name().equalsIgnoreCase(o.toString())) { this.value = (T) c; break; }
        }
    }
}
