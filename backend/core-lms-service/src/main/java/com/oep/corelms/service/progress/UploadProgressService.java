package com.oep.corelms.service.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class UploadProgressService {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Đăng ký một SSE emitter cho contentId cụ thể.
     */
    public void register(UUID contentId, SseEmitter emitter) {
        emitters.computeIfAbsent(contentId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(contentId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    emitters.remove(contentId);
                }
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Gửi event ping khởi tạo kết nối
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("Connected to progress stream for content " + contentId));
        } catch (IOException e) {
            emitter.complete();
        }
    }

    /**
     * Gửi event tiến độ đến tất cả client đang lắng nghe contentId.
     *
     * @param contentId ID của LessonContent
     * @param eventName Tên sự kiện (progress, done, error)
     * @param data      Dữ liệu sự kiện
     */
    public void sendEvent(UUID contentId, String eventName, Object data) {
        List<SseEmitter> list = emitters.get(contentId);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                log.debug("Lỗi gửi SSE event '{}' tới emitter: {}", eventName, e.getMessage());
                emitter.complete();
                list.remove(emitter);
            }
        }
    }

    /**
     * Hoàn tất tất cả emitter của contentId (khi video transcode DONE hoặc FAILED).
     */
    public void complete(UUID contentId) {
        List<SseEmitter> list = emitters.remove(contentId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }
}
