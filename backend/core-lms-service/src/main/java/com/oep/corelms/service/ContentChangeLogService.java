package com.oep.corelms.service;

import com.oep.corelms.repository.ContentChangeLogRepository;
import com.oep.corelms.repository.LessonContentRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.course.LessonContent;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.system.ContentChangeLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Version history of lesson content: every create / edit / file replacement is recorded with who and when. */
@Service
@RequiredArgsConstructor
public class ContentChangeLogService {

    public record Entry(String action, int version, Instant changedAt, String changedBy) {}

    private final ContentChangeLogRepository logs;
    private final LessonContentRepository contents;
    private final UserRepository users;

    /** CREATED keeps version 1; UPDATED / FILE_REPLACED bump the content's version before logging it. */
    @Transactional
    public void record(UUID contentId, UUID adminId, String action) {
        LessonContent content = contents.findById(contentId).orElse(null);
        if (content == null) return;
        if (!"CREATED".equals(action)) {
            content.setVersionNumber(content.getVersionNumber() + 1);
            contents.save(content);
        }
        saveEntry(content, adminId, action);
    }

    /** First file completion records an upload without changing the version already enqueued for M7. */
    @Transactional
    public void recordCurrentVersion(UUID contentId, UUID adminId, String action) {
        LessonContent content = contents.findById(contentId).orElse(null);
        if (content == null) return;
        saveEntry(content, adminId, action);
    }

    private void saveEntry(LessonContent content, UUID adminId, String action) {
        ContentChangeLog log = new ContentChangeLog();
        log.setContent(content);
        log.setChangedBy(users.getReferenceById(adminId));
        log.setAction(action);
        log.setVersionNumber(content.getVersionNumber());
        logs.save(log);
    }

    @Transactional(readOnly = true)
    public List<Entry> history(UUID contentId) {
        return logs.findByContent_IdOrderByChangedAtDesc(contentId).stream()
                .map(l -> new Entry(l.getAction(), l.getVersionNumber(), l.getChangedAt(), l.getChangedBy() == null ? null : l.getChangedBy().getEmail()))
                .toList();
    }
}
