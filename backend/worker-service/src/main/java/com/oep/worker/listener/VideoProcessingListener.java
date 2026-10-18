package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.VideoUploadedEvent;
import com.oep.worker.service.VideoOrchestrationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoProcessingListener {

    private final VideoOrchestrationService videoOrchestrationService;

    @RabbitListener(queues = MessagingConstants.QUEUE_VIDEO_PROCESS)
    public void onVideoUploaded(
            VideoUploadedEvent event,
            Message message,
            Channel channel,
            @Header(value = AmqpHeaders.DELIVERY_TAG, required = false) Long deliveryTag
    ) throws IOException {
        log.info("Nhận VideoUploadedEvent: contentId={}, jobId={}", event.contentId(), event.jobId());

        try {
            videoOrchestrationService.process(event);

            if (channel != null && deliveryTag != null) {
                channel.basicAck(deliveryTag, false);
            }
        } catch (Exception e) {
            log.error("Lỗi khi xử lý video jobId={}: {}", event.jobId(), e.getMessage());

            if (channel != null && deliveryTag != null) {
                // Không requeue -> RabbitMQ tự động đẩy message sang DLQ
                channel.basicNack(deliveryTag, false, false);
            }

            throw new AmqpRejectAndDontRequeueException("Transcoding thất bại, chuyển vào DLQ", e);
        }
    }
}
