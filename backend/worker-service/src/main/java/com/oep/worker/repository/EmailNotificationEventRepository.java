package com.oep.worker.repository;

import com.oep.coremodel.entity.notification.EmailNotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailNotificationEventRepository extends JpaRepository<EmailNotificationEvent, UUID> {
}
