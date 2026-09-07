package com.banking.authservice.controller;

import com.banking.authservice.dto.AuthResponse;
import com.banking.authservice.dto.LoginRequest;
import com.banking.authservice.dto.RefreshRequest;
import com.banking.authservice.dto.RegisterRequest;
import com.banking.authservice.dto.ServiceTokenRequest;
import com.banking.authservice.dto.ServiceTokenResponse;
import com.banking.authservice.service.AuthService;
import com.banking.authservice.service.ServiceAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final ServiceAuthService serviceAuthService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResponse> serviceToken(@Valid @RequestBody ServiceTokenRequest request) {
        return ResponseEntity.ok(serviceAuthService.issueServiceToken(request));
    }
}