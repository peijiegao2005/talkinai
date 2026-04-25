package com.talkingai.soulchat.security;

import com.talkingai.soulchat.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 跳过不需要认证的路径
        if (path.startsWith("/api/auth/") || path.startsWith("/ws/")) {
            return chain.filter(exchange);
        }

        String token = extractToken(request);

        if (token == null) {
            log.debug("No JWT token found in request: {}", path);
            return chain.filter(exchange);
        }

        if (!jwtUtil.validateToken(token)) {
            log.warn("Invalid JWT token for request: {}", path);
            return chain.filter(exchange);
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

    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
