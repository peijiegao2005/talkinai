package com.talkingai.soulchat.config;

import com.talkingai.soulchat.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(csrf -> csrf.disable())
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/", "/index.html", "/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        .pathMatchers("/api/auth/**", "/ws/**").permitAll()
                        .pathMatchers("/api/public/**").permitAll()
                        .anyExchange().authenticated()
                )
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> {
                            exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                            exchange.getResponse().getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                            String body = "{\"code\":401,\"message\":\"请先登录\",\"data\":null}";
                            return exchange.getResponse().writeWith(
                                    reactor.core.publisher.Mono.just(
                                            exchange.getResponse().bufferFactory().wrap(body.getBytes())
                                    )
                            );
                        })
                )
                .build();
    }
}
