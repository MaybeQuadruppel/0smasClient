package com.OsamaClient.newbridge.Utils.Render.Events;

public interface IEventBus {
    void subscribe(Object subscriber);
    void unsubscribe(Object subscriber);
    void post(Object event);
    default void registerLambdaFactory(String pkg, Object factory) {}
}