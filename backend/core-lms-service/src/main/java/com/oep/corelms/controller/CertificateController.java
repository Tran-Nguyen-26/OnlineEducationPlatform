package com.oep.corelms.controller;

import com.oep.corelms.dto.student.CertificateResponse;
import com.oep.corelms.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated: anyone with a certificate number can check it's real — no student/course data beyond the certificate itself. */
@RestController
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/api/certificates/verify/{certificateNumber}")
    public CertificateResponse verify(@PathVariable String certificateNumber) {
        return certificateService.verify(certificateNumber);
    }
}
