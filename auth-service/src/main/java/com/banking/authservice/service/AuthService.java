package com.banking.authservice.service;

import com.banking.authservice.dto.AuthResponse;
import com.banking.authservice.dto.LoginRequest;
import com.banking.authservice.dto.RegisterRequest;
import com.banking.authservice.entity.User;
import com.banking.authservice.entity.UserRole;
import com.banking.authservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisTemplate<String, String> redisTemplate;

    public void register(RegisterRequest request) {
        log.info("Registering user: {}", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A user already exists for email: " + request.getEmail());
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(UserRole.CUSTOMER);
        user.setEnabled(true);
        user.setAccountLocked(false);
        userRepository.save(user);
        log.info("Registered user: {}", user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        if (!user.isEnabled()) {
            throw new IllegalStateException("Account is disabled for: " + user.getEmail());
        }
        if (user.isAccountLocked()) {
            throw new IllegalStateException("Account is locked for: " + user.getEmail());
        }

        return issueTokenPair(user);
    }

    public AuthResponse refresh(String refreshToken) {
        Claims claims = jwtService.parseAndValidate(refreshToken);
        String jti = claims.getId();
        String userId = claims.getSubject();

        String storedUserId = redisTemplate.opsForValue().get(REFRESH_KEY_PREFIX + jti);
        if (storedUserId == null || !storedUserId.equals(userId)) {
            throw new IllegalArgumentException("Refresh token is invalid or has been revoked");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        redisTemplate.delete(REFRESH_KEY_PREFIX + jti);
        return issueTokenPair(user);
    }

    public void logout(String refreshToken) {
        Claims claims = jwtService.parseAndValidate(refreshToken);
        redisTemplate.delete(REFRESH_KEY_PREFIX + claims.getId());
    }

    private AuthResponse issueTokenPair(User user) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
        JwtService.IssuedRefreshToken refreshToken = jwtService.issueRefreshToken(user.getId());

        redisTemplate.opsForValue().set(
                REFRESH_KEY_PREFIX + refreshToken.jti(),
                user.getId(),
                refreshToken.ttl().toSeconds(),
                TimeUnit.SECONDS
        );

        return new AuthResponse(accessToken, refreshToken.token(), jwtService.accessTokenTtlSeconds(), user.getRole().name());
    }
}