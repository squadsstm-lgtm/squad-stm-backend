package com.squad.backend.config;

import com.squad.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/signup", "/api/auth/signin",
                    "/api/auth/google-login", "/api/auth/verifyToken",
                    "/api/auth/oauth/token", "/api/auth/verifyEmail/**",
                    "/api/auth/logout",
                    "/api/auth/check-email", "/api/auth/check-username",
                    "/api/auth/check-phone", "/api/auth/check-clubName",
                    "/api/auth/forgotPassword", "/api/auth/reset-password",
                    "/api/auth/resend-verification",
                    "/api/auth/validate-accessToken",
                    "/api/auth/mpin/forgot/validate", "/api/auth/mpin/forgot/reset",
                    "/api/auth/seed-controller").permitAll()
                .requestMatchers("/api/webhook/**").permitAll()
                .requestMatchers("/api/seasons/current").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/players/*/*").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/players/*/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/*/*").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/users/*/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/invite/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/invite/*/select-player").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/invite/*/complete").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/requests/*/*").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/requests/update").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/create-payment-intent").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/confirm-payment").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/payments/invoices/from-request/*/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/payments/invoices/*/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/invoices/create-payment-intent").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/invoices/confirm-payment").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
