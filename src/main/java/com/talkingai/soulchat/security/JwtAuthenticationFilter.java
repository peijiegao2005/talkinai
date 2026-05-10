package com.talkingai.soulchat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkingai.soulchat.dto.ApiResponse;
import com.talkingai.soulchat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 跳过不需要认证的路径
        if (path.startsWith("/api/auth/") || path.startsWith("/ws/") ||
            path.startsWith("/css/") || path.startsWith("/js/") ||
            path.startsWith("/images/") || path.equals("/") ||
            path.equals("/index.html") || path.equals("/favicon.ico")) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);

        if (token == null) {
            log.debug("No JWT token found in request: {}", path);
            return writeUnauthorizedResponse(exchange.getResponse(), "请先登录");
        }

        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for request: {}", path);
            return writeUnauthorizedResponse(exchange.getResponse(), "登录已过期，请重新登录");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        log.debug("Authenticated user: {}, userId: {}", username, userId);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private Mono<Void> writeUnauthorizedResponse(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            ApiResponse<?> apiResponse = ApiResponse.error(401, message);
            String json = objectMapper.writeValueAsString(apiResponse);
            DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write unauthorized response", e);
            return response.setComplete();
        }
    }

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
