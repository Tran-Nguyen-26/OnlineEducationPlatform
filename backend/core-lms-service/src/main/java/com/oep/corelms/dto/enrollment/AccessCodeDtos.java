package com.oep.corelms.dto.enrollment;

import com.oep.coremodel.entity.enrollment.AccessCode;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccessCodeDtos {
    private AccessCodeDtos() {}

    public record RedeemRequest(@NotBlank @Size(max = 64) String code) {}

    public record CreateBatch(
            @NotNull UUID courseId,
            @NotNull AccessCode.Kind kind,
            @Min(1) @Max(500) int count,
            @Min(90) @Max(180) int accessDays,
            @NotNull @Future Instant expiresAt
    ) {}

    public record IssuedCode(UUID id, String code) {}

    public record Batch(UUID batchId, UUID courseId, AccessCode.Kind kind, int accessDays, Instant expiresAt,
                        List<IssuedCode> codes) {}

    public record CodeStatus(UUID id, UUID courseId, AccessCode.Kind kind, AccessCode.Status status,
                             int accessDays, Instant expiresAt, Instant usedAt) {
        public static CodeStatus from(AccessCode code, Instant now) {
            return new CodeStatus(code.getId(), code.getCourseId(), code.getKind(), code.statusAt(now),
                    code.getAccessDays(), code.getExpiresAt(), code.getUsedAt());
        }
    }
}
