package org.healthily.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.healthily.demo.model.dto.LoginRequest;
import org.healthily.demo.model.dto.LoginResponse;
import org.healthily.demo.model.dto.RegisterRequest;
import org.healthily.demo.model.dto.RegisterResponse;
import org.healthily.demo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
} 