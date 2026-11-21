package com.oep.worker.config;

import com.oep.coremodel.constant.MessagingConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiIndexMessagingConfigTest {
    @Test
    void declaresDurableIndexQueueWithDeadLetterBinding() {
        MessagingConfig config = new MessagingConfig();
        var exchange = config.oepExchange();
        var deadExchange = config.deadLetterExchange();
        var queue = config.aiIndexQueue();
        var deadQueue = config.aiIndexDeadLetterQueue();

        assertEquals(MessagingConstants.QUEUE_AI_INDEX, queue.getName());
        assertEquals(true, queue.isDurable());
        assertEquals(MessagingConstants.DLX, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(MessagingConstants.DLQ_AI_INDEX_KEY,
                queue.getArguments().get("x-dead-letter-routing-key"));
        assertEquals(MessagingConstants.QUEUE_AI_INDEX_DLQ, deadQueue.getName());
        assertEquals(true, deadQueue.isDurable());
        assertEquals(MessagingConstants.RK_AI_INDEX,
                config.aiIndexBinding(queue, exchange).getRoutingKey());
        assertEquals(MessagingConstants.DLQ_AI_INDEX_KEY,
                config.aiIndexDeadLetterBinding(deadQueue, deadExchange).getRoutingKey());
    }
}
