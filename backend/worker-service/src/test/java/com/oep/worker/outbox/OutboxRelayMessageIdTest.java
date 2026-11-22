package com.oep.worker.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.system.OutboxEvent;
import com.oep.worker.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OutboxRelayMessageIdTest {
    private final OutboxRelay relay = new OutboxRelay(
            mock(OutboxEventRepository.class), mock(RabbitTemplate.class),
            mock(MeterRegistry.class), new ObjectMapper());

    @Test
    void aiDeliveryUsesPayloadEventIdWhileOtherEventsUseOutboxRowId() {
        UUID rowId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent();
        event.setId(rowId);
        event.setEventType(MessagingConstants.RK_AI_INDEX);
        event.setPayload("{\"event_id\":\"" + eventId + "\"}");
        assertEquals(eventId.toString(), relay.messageIdFor(event));

        event.setEventType(MessagingConstants.RK_NOTIFICATION);
        assertEquals(rowId.toString(), relay.messageIdFor(event));
    }

    @Test
    void malformedAiEventCannotBePublishedWithAnUnrelatedMessageId() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setEventType(MessagingConstants.RK_AI_INDEX);
        event.setPayload("{\"event_id\":\"wrong\"}");
        assertThrows(IllegalArgumentException.class, () -> relay.messageIdFor(event));
    }
}
