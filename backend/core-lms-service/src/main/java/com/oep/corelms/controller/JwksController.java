package com.oep.corelms.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.oep.corelms.security.JwtKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Public key set so api-gateway / other services can validate tokens issued by this service. */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyService keyService;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return keyService.publicJwkSet().toJSONObject();
    }
}
