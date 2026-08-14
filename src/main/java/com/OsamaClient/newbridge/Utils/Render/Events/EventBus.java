package com.OsamaClient.newbridge.Utils.Render.Events;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventBus implements IEventBus {
    private final Map<Class<?>, List<Subscription>> listeners = new ConcurrentHashMap<>();

    @Override
    public void subscribe(Object subscriber) {
        Class<?> clazz = subscriber.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)) {
                if (method.getParameterCount() != 1) continue;
                method.setAccessible(true);
                Class<?> eventType = method.getParameterTypes()[0];

                listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                        .add(new Subscription(subscriber, method));
            }
        }
    }

    @Override
    public void unsubscribe(Object subscriber) {
        for (List<Subscription> subs : listeners.values()) {
            subs.removeIf(sub -> sub.subscriber == subscriber);
        }
    }

    @Override
    public void post(Object event) {
        List<Subscription> subs = listeners.get(event.getClass());
        if (subs != null) {
            for (Subscription sub : new ArrayList<>(subs)) {
                try {
                    sub.method.invoke(sub.subscriber, event);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class Subscription {
        final Object subscriber;
        final Method method;

        Subscription(Object subscriber, Method method) {
            this.subscriber = subscriber;
            this.method = method;
        }
    }
}