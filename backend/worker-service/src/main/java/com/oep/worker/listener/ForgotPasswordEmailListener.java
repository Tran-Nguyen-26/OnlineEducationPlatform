package com.oep.worker.listener;

import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.event.ForgotPasswordEmailEvent;
import com.oep.worker.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ForgotPasswordEmailListener {

    private final EmailService emailService;

    @RabbitListener(queues = MessagingConstants.QUEUE_FORGOT_PASSWORD_EMAIL)
    public void onForgotPasswordEmail(ForgotPasswordEmailEvent event) {
        log.info("Received ForgotPasswordEmailEvent: type={} userId={}", event.eventType(), event.userId());

        switch (event.eventType()) {
            case "FORGOT_PASSWORD_OTP" -> {
                if (event.otpCode() == null || event.otpCode().isBlank()) {
                    log.error("FORGOT_PASSWORD_OTP event missing otpCode for userId={}", event.userId());
                    return;
                }
                emailService.sendForgotPasswordOtp(
                        event.recipientEmail(),
                        event.fullName(),
                        event.otpCode()
                );
            }
            case "PASSWORD_RESET_SUCCESS_ALERT" -> {
                emailService.sendPasswordResetSuccessAlert(
                        event.recipientEmail(),
                        event.fullName(),
                        event.actionTime()
                );
            }
            default -> log.warn("Unknown ForgotPasswordEmailEvent type: {}", event.eventType());
        }
    }
}
