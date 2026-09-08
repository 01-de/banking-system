package com.banking.authcommon.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;

public final class JwtDecoderFactory {

    private JwtDecoderFactory() {
    }

    public static JwtDecoder fromPublicKeyPem(String publicKeyPem) {
        RSAPublicKey publicKey = (RSAPublicKey) PemKeyUtils.parsePublicKey(publicKeyPem);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}