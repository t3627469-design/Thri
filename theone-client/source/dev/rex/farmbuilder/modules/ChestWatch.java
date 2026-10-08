package dev.rex.farmbuilder.modules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** Compares what a chest held when the bot last left it with what it holds now. */
final class ChestWatch {
    private ChestWatch() {}

    /** Items whose count dropped between {@code before} and {@code after}, mapped to how many are gone. */
    static <K> Map<K, Integer> shortfall(Map<K, Integer> before, Map<K, Integer> after) {
        Map<K, Integer> gone = new LinkedHashMap<>();
        if (before == null) return gone;
        before.forEach((item, was) -> {
            int now = after == null ? 0 : after.getOrDefault(item, 0);
            if (was != null && was > now) gone.put(item, was - now);
        });
        return gone;
    }

    static <K> String describe(Map<K, Integer> items, Function<K, String> name) {
        StringBuilder sb = new StringBuilder();
        items.forEach((item, count) -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(count).append("x ").append(name.apply(item));
        });
        return sb.toString();
    }
}
