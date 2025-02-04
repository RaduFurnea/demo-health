package org.healthily.demo.service;

import lombok.RequiredArgsConstructor;
import org.healthily.demo.exception.BadRequestException;
import org.healthily.demo.model.dto.LoginRequest;
import org.healthily.demo.model.dto.LoginResponse;
import org.healthily.demo.model.dto.RegisterRequest;
import org.healthily.demo.model.dto.RegisterResponse;
import org.healthily.demo.repository.UserRepository;
import org.healthily.demo.security.User;
import org.healthily.demo.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public RegisterResponse register(RegisterRequest request) {
        User user = userRepository.findByEmail(request.getEmail());
        if (user != null) {
            throw new BadRequestException("User already exists");
        }
        userRepository.createUser(request);
        return RegisterResponse.builder()
                .message("User successfully created")
                .build();
    }

    public LoginResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail());
        if (user == null || !passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        return LoginResponse.builder().accessToken(jwtUtil.generateToken(user))
                .userId(user.getId())
                .build();
    }
} 