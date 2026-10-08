package dev.thri.event.events;

import dev.thri.event.Event;

public class KeyEvent extends Event {
    public final int key, action, modifiers;
    public KeyEvent(int k, int a, int m) { key = k; action = a; modifiers = m; }
}
