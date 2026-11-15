package com.oep.corelms.dto.admin;

import java.util.UUID;

/** temporaryPassword is only ever returned here, once; the admin relays it to the teacher. */
public record CreateTeacherResponse(
        UUID userId,
        String email,
        String fullName,
        String temporaryPassword) {
}
