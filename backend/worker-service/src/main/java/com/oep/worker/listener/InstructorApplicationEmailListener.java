package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.InstructorApplicationApprovedEvent;
import com.oep.coremodel.event.InstructorApplicationRejectedEvent;
import com.oep.worker.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queues = MessagingConstants.QUEUE_INSTRUCTOR_APPLICATION)
public class InstructorApplicationEmailListener {

    private final EmailService emailService;

    @RabbitHandler
    public void onApproved(InstructorApplicationApprovedEvent event) {
        log.info("Received InstructorApplicationApprovedEvent for applicationId={} email={}",
                event.applicationId(), event.recipientEmail());
        try {
            emailService.sendInstructorApplicationApproved(
                    event.recipientEmail(),
                    event.fullName(),
                    event.temporaryPassword(),
                    event.loginUrl()
            );
        } catch (Exception e) {
            log.error("Failed to send instructor approved email to {}: {}", event.recipientEmail(), e.getMessage(), e);
        }
    }

    @RabbitHandler
    public void onRejected(InstructorApplicationRejectedEvent event) {
        log.info("Received InstructorApplicationRejectedEvent for applicationId={} email={}",
                event.applicationId(), event.recipientEmail());
        try {
            emailService.sendInstructorApplicationRejected(
                    event.recipientEmail(),
                    event.fullName(),
                    event.rejectionReason()
            );
        } catch (Exception e) {
            log.error("Failed to send instructor rejected email to {}: {}", event.recipientEmail(), e.getMessage(), e);
        }
    }
}
