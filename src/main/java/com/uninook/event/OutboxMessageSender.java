package com.uninook.event;

public interface OutboxMessageSender {

    void send(OutboxEventType eventType, Object event);
}
