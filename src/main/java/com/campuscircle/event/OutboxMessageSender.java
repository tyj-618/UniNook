package com.campuscircle.event;

public interface OutboxMessageSender {

    void send(OutboxEventType eventType, Object event);
}
