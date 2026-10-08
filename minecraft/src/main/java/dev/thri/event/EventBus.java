package dev.thri.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <E extends Event> void subscribe(Class<E> type, Consumer<E> fn) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(fn);
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> E post(E event) {
        List<Consumer<?>> ls = listeners.get(event.getClass());
        if (ls != null) for (Consumer<?> c : ls) ((Consumer<E>) c).accept(event);
        return event;
    }
}
