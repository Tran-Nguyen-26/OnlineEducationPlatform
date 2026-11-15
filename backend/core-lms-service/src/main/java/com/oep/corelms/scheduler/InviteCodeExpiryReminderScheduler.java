package com.oep.corelms.scheduler;

import com.oep.corelms.repository.CourseInviteCodeRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.registration.CourseInviteCode;
import com.oep.coremodel.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reminds the admin who issued a code before it expires, so they can extend it or send a new one in time. */
@Component
@RequiredArgsConstructor
@Slf4j
public class InviteCodeExpiryReminderScheduler {

    private static final long REMIND_WINDOW_HOURS = 24;

    private final CourseInviteCodeRepository inviteCodes;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional(readOnly = true)
    public void remindExpiringCodes() {
        Instant now = Instant.now();
        List<CourseInviteCode> expiringSoon = inviteCodes.findByActiveTrueAndExpiresAtBetween(now, now.plus(REMIND_WINDOW_HOURS, ChronoUnit.HOURS));
        for (CourseInviteCode code : expiringSoon) {
            if (code.getCreatedBy() == null) continue;

            NotificationRequestedEvent event = new NotificationRequestedEvent(
                    code.getCreatedBy().getId(), "INVITE_CODE_EXPIRING",
                    "Mã mời sắp hết hạn — " + code.getCourse().getTitle(),
                    "Mã \"" + code.getCode() + "\" của khóa \"" + code.getCourse().getTitle() + "\" sẽ hết hạn vào " + code.getExpiresAt() + ".",
                    "invite-code-expiring:" + code.getId(), "COURSE", code.getCourse().getId(), Instant.now());
            rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_NOTIFICATION, event);
        }
        if (!expiringSoon.isEmpty()) {
            log.info("Queued {} invite-code-expiring reminder(s)", expiringSoon.size());
        }
    }
}
