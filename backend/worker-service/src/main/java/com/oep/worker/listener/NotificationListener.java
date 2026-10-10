package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.NotificationRequestedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationListener {

    @RabbitListener(queues = MessagingConstants.QUEUE_NOTIFICATION)
    public void onNotification(NotificationRequestedEvent event) {
        // TODO: persist Notification row (dashboard card) + send email / push
        log.info("Notification requested for user {}: {}", event.userId(), event.title());
    }
}
