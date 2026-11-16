package com.oep.corelms.repository;

import com.oep.coremodel.entity.social.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {
    List<MessageAttachment> findByMessage_Id(UUID messageId);

    long countByMessage_Id(UUID messageId);
}
