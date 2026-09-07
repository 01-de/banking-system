package com.banking.authservice.service;

import com.banking.authservice.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final PrivateKey jwtPrivateKey;
    private final PublicKey jwtPublicKey;

    @Value("${jwt.access-token-ttl-minutes}")
    private long accessTokenTtlMinutes;

    @Value("${jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @Value("${jwt.service-token-ttl-minutes}")
    private long serviceTokenTtlMinutes;

    public String issueAccessToken(String userId, String email, UserRole role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("role", role.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessTokenTtlMinutes))))
                .signWith(jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    public IssuedRefreshToken issueRefreshToken(String userId) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Duration ttl = Duration.ofDays(refreshTokenTtlDays);
        String token = Jwts.builder()
                .subject(userId)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
        return new IssuedRefreshToken(token, jti, ttl);
    }

    public String issueServiceToken(String serviceId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(serviceId)
                .claim("role", "SERVICE")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(serviceTokenTtlMinutes))))
                .signWith(jwtPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    public long accessTokenTtlSeconds() {
        return Duration.ofMinutes(accessTokenTtlMinutes).toSeconds();
    }

    public long serviceTokenTtlSeconds() {
        return Duration.ofMinutes(serviceTokenTtlMinutes).toSeconds();
    }

    public Claims parseAndValidate(String token) throws SignatureException {
        return Jwts.parser()
                .verifyWith(jwtPublicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public record IssuedRefreshToken(String token, String jti, Duration ttl) {
    }
}