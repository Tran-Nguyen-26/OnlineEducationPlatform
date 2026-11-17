package com.oep.coremodel.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.entity.system.OutboxEvent;

import java.util.UUID;

/** Builds {@link OutboxEvent} rows. event_type holds the RabbitMQ routing key the relay will publish with. */
public final class OutboxEvents {

    private OutboxEvents() {}

    public static OutboxEvent notification(NotificationRequestedEvent event, ObjectMapper mapper) {
        OutboxEvent o = new OutboxEvent();
        o.setAggregateType("NOTIFICATION");
        o.setAggregateId(event.userId() != null ? event.userId() : UUID.randomUUID());
        o.setEventType(com.oep.coremodel.constant.MessagingConstants.RK_NOTIFICATION);
        try {
            o.setPayload(mapper.writeValueAsString(event));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise notification event", e);
        }
        return o;
    }
}
