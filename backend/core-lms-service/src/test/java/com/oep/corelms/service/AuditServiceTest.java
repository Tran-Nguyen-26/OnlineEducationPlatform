package com.oep.corelms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oep.corelms.repository.AuditLogRepository;
import com.oep.corelms.repository.UserRepository;
import com.oep.coremodel.entity.identity.User;
import com.oep.coremodel.entity.system.AuditLog;
import com.oep.coremodel.enums.AuditAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditService auditService;

    private UUID actorId;
    private UUID targetId;
    private User mockActor;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        mockActor = new User();
        mockActor.setId(actorId);
    }

    @Test
    @DisplayName("Should successfully record audit log with object metadata")
    void record_Success_WithMetadataObject() throws Exception {
        Map<String, Object> meta = Map.of("reason", "Instructor violated community standards");
        when(userRepository.getReferenceById(actorId)).thenReturn(mockActor);
        when(objectMapper.writeValueAsString(meta)).thenReturn("{\"reason\":\"Instructor violated community standards\"}");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AuditLog result = auditService.record(actorId, AuditAction.DISABLE_USER, "USER", targetId, meta);

        assertNotNull(result);
        assertEquals(AuditAction.DISABLE_USER, result.getAction());
        assertEquals("USER", result.getTargetType());
        assertEquals(targetId, result.getTargetId());
        assertEquals("{\"reason\":\"Instructor violated community standards\"}", result.getMetadata());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should successfully record audit log when metadata is null")
    void record_Success_WithNullMetadata() {
        when(userRepository.getReferenceById(actorId)).thenReturn(mockActor);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });

        AuditLog result = auditService.record(actorId, AuditAction.APPROVE_COURSE, "COURSE", targetId);

        assertNotNull(result);
        assertNull(result.getMetadata());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should pass through metadata directly if already a JSON String")
    void record_Success_WithStringMetadata() {
        String jsonStr = "{\"score\": 95}";
        when(userRepository.getReferenceById(actorId)).thenReturn(mockActor);
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog result = auditService.record(actorId, AuditAction.ADJUST_SCORE, "SUBMISSION", targetId, jsonStr);

        assertNotNull(result);
        assertEquals(jsonStr, result.getMetadata());
        verifyNoInteractions(objectMapper);
    }

    @Test
    @DisplayName("NFR/Exception Isolation: Repository failure must not throw exception to caller and returns null")
    void record_ExceptionInRepository_DoesNotThrowAndReturnsNull() {
        when(userRepository.getReferenceById(actorId)).thenReturn(mockActor);
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new DataIntegrityViolationException("Database constraint violation"));

        AuditLog result = assertDoesNotThrow(() ->
                auditService.record(actorId, AuditAction.DISABLE_COURSE, "COURSE", targetId, null)
        );

        assertNull(result, "Service must return null and not fail the calling business transaction");
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("NFR/Exception Isolation: JSON serialization error must not throw exception and returns null")
    void record_JsonProcessingException_DoesNotThrowAndReturnsNull() throws Exception {
        Object invalidObject = new Object();
        when(userRepository.getReferenceById(actorId)).thenReturn(mockActor);
        when(objectMapper.writeValueAsString(invalidObject))
                .thenThrow(new JsonProcessingException("Cannot serialize circular reference") {});

        AuditLog result = assertDoesNotThrow(() ->
                auditService.record(actorId, AuditAction.REGRADE, "ASSIGNMENT", targetId, invalidObject)
        );

        assertNull(result);
        verify(auditLogRepository, never()).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("Should skip and return null when mandatory parameters are null")
    void record_MissingMandatoryFields_SkipsAndReturnsNull() {
        assertNull(auditService.record(null, AuditAction.DISABLE_USER, "USER", targetId, null));
        assertNull(auditService.record(actorId, null, "USER", targetId, null));
        assertNull(auditService.record(actorId, AuditAction.DISABLE_USER, null, targetId, null));
        assertNull(auditService.record(actorId, AuditAction.DISABLE_USER, "USER", null, null));
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("Should query audit logs by actorId")
    void getLogsByActor_CallsRepository() {
        AuditLog log1 = new AuditLog();
        when(auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId)).thenReturn(List.of(log1));

        List<AuditLog> logs = auditService.getLogsByActor(actorId);

        assertEquals(1, logs.size());
        verify(auditLogRepository).findByActorIdOrderByCreatedAtDesc(actorId);
    }

    @Test
    @DisplayName("Should query audit logs by targetType and targetId")
    void getLogsByTarget_CallsRepository() {
        AuditLog log1 = new AuditLog();
        when(auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("COURSE", targetId))
                .thenReturn(List.of(log1));

        List<AuditLog> logs = auditService.getLogsByTarget("COURSE", targetId);

        assertEquals(1, logs.size());
        verify(auditLogRepository).findByTargetTypeAndTargetIdOrderByCreatedAtDesc("COURSE", targetId);
    }

    @Test
    @DisplayName("Should query audit logs by action")
    void getLogsByAction_CallsRepository() {
        AuditLog log1 = new AuditLog();
        when(auditLogRepository.findByActionOrderByCreatedAtDesc(AuditAction.REGRADE))
                .thenReturn(List.of(log1));

        List<AuditLog> logs = auditService.getLogsByAction(AuditAction.REGRADE);

        assertEquals(1, logs.size());
        verify(auditLogRepository).findByActionOrderByCreatedAtDesc(AuditAction.REGRADE);
    }
}
