package com.oep.corelms.service;

import com.oep.corelms.dto.admin.BroadcastRequest;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.constant.MessagingConstants;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.enums.AccountStatus;
import com.oep.coremodel.enums.UserRole;
import com.oep.coremodel.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Admin-wide announcement delivered as an in-app notification to every ACTIVE account in the chosen audience. */
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final UserRepository users;
    private final RabbitTemplate rabbitTemplate;

    @Transactional(readOnly = true)
    public int broadcast(BroadcastRequest request) {
        List<UserRole> roles = switch (request.audience()) {
            case STUDENTS -> List.of(UserRole.STUDENT);
            case INSTRUCTORS -> List.of(UserRole.INSTRUCTOR);
            case ALL -> List.of(UserRole.STUDENT, UserRole.INSTRUCTOR);
        };
        String batchId = UUID.randomUUID().toString();
        List<User> recipients = users.findByRoleInAndAccountStatus(roles, AccountStatus.ACTIVE);
        for (User u : recipients) {
            rabbitTemplate.convertAndSend(MessagingConstants.EXCHANGE, MessagingConstants.RK_NOTIFICATION,
                    new NotificationRequestedEvent(u.getId(), "SYSTEM_ANNOUNCEMENT", request.title().trim(), request.message().trim(),
                            "broadcast:" + batchId + ":" + u.getId(), "SYSTEM", null, Instant.now()));
        }
        return recipients.size();
    }
}
