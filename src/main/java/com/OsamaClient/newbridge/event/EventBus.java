package com.OsamaClient.newbridge.event;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<Class<?>, List<Listener>> listeners = new HashMap<>();

    public void subscribe(Object subscriber) {
        for (Method method : subscriber.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Subscribe.class) && method.getParameterCount() == 1) {
                method.setAccessible(true); // WICHTIG: Erlaubt Aufruf von private & protected Methoden
                Class<?> eventType = method.getParameterTypes()[0];
                listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                        .add(new Listener(subscriber, method));
            }
        }
    }

    public void post(Object event) {
        List<Listener> eventListeners = listeners.get(event.getClass());
        if (eventListeners != null) {
            for (Listener listener : eventListeners) {
                try {
                    listener.method.invoke(listener.target, event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private record Listener(Object target, Method method) {}
}