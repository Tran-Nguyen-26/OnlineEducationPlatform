package com.oep.coremodel.entity.ai;

import com.oep.coremodel.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.oep.coremodel.enums.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ai_messages", indexes = {@Index(name = "idx_ai_messages_conversation_id", columnList = "conversation_id")})
public class AiMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversation conversation;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cited_chunk_ids", nullable = true, columnDefinition = "jsonb")
    private String citedChunkIds;

    @Column(name = "prompt_tokens", nullable = true)
    private Integer promptTokens;

    @Column(name = "completion_tokens", nullable = true)
    private Integer completionTokens;

    @Column(name = "latency_ms", nullable = true)
    private Integer latencyMs;

    @Column(name = "is_refused", nullable = false)
    private boolean refused = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
