package com.talkingai.soulchat.service;

import com.talkingai.soulchat.dto.*;
import com.talkingai.soulchat.entity.User;
import com.talkingai.soulchat.repository.UserRepository;
import com.talkingai.soulchat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public Mono<ApiResponse<AuthResponse>> register(RegisterRequest request) {
        return userRepository.existsByUsername(request.getUsername())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.just(ApiResponse.<AuthResponse>error(400, "用户名已存在"));
                    }
                    return userRepository.existsByEmail(request.getEmail());
                })
                .flatMap(exists -> {
                    if (exists instanceof ApiResponse) {
                        return Mono.just((ApiResponse<AuthResponse>) exists);
                    }
                    if ((Boolean) exists) {
                        return Mono.just(ApiResponse.<AuthResponse>error(400, "邮箱已被注册"));
                    }
                    
                    User user = new User();
                    user.setUsername(request.getUsername());
                    user.setEmail(request.getEmail());
                    user.setPassword(passwordEncoder.encode(request.getPassword()));
                    user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
                    user.setHasAssessment(false);
                    user.setOnline(false);
                    
                    return userRepository.save(user)
                            .map(savedUser -> {
                                String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getUsername());
                                AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .type("Bearer")
                                        .userId(savedUser.getId())
                                        .username(savedUser.getUsername())
                                        .nickname(savedUser.getNickname())
                                        .hasAssessment(false)
                                        .build();
                                return ApiResponse.success(response);
                            });
                });
    }

    public Mono<ApiResponse<AuthResponse>> login(LoginRequest request) {
        return userRepository.findByUsername(request.getUsername())
                .flatMap(user -> {
                    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                        return Mono.just(ApiResponse.<AuthResponse>error(401, "密码错误"));
                    }
                    
                    user.setOnline(true);
                    user.setLastActiveAt(System.currentTimeMillis());
                    
                    return userRepository.save(user)
                            .map(updatedUser -> {
                                String token = jwtUtil.generateToken(updatedUser.getId(), updatedUser.getUsername());
                                AuthResponse response = AuthResponse.builder()
                                        .token(token)
                                        .type("Bearer")
                                        .userId(updatedUser.getId())
                                        .username(updatedUser.getUsername())
                                        .nickname(updatedUser.getNickname())
                                        .hasAssessment(updatedUser.getHasAssessment())
                                        .build();
                                return ApiResponse.success(response);
                            });
                })
                .switchIfEmpty(Mono.just(ApiResponse.<AuthResponse>error(401, "用户不存在")));
    }
}
