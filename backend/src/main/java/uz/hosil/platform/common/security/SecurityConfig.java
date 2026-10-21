package uz.hosil.platform.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import uz.hosil.platform.common.error.ErrorEnvelope;

/**
 * Spring Security configuration establishing:
 * 1. Stateless security filter chain with JWT authentication for mobile REST APIs (/api/**).
 * 2. Session-based filter chain for operational Admin panel (/admin/**) per ADR-002 and DEC-016.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health").permitAll()
                .requestMatchers("/api/v1/auth/otp/**").permitAll()
                .requestMatchers("/api/v1/auth/telegram/**").permitAll()
                .requestMatchers("/api/v1/auth/token/refresh").permitAll()
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/market/harvest-listings", "/api/v1/market/harvest-listings/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/v1/market/harvest-listings", "/api/v1/market/harvest-listings/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/market/catalog", "/api/v1/market/catalog/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/v1/market/catalog", "/api/v1/market/catalog/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    ErrorEnvelope envelope = ErrorEnvelope.of(
                            "AUTHENTICATION_REQUIRED",
                            "Avtorizatsiyadan oʻtish talab etiladi",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), envelope);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    ErrorEnvelope envelope = ErrorEnvelope.of(
                            "INSUFFICIENT_PERMISSIONS",
                            "Kirish huquqi yetarli emas",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), envelope);
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                .requestMatchers("/admin/login").permitAll()
                .requestMatchers("/admin", "/admin/**").hasAnyRole("ADMIN", "ADMINISTRATOR", "ADMIN_OPERATOR", "ADMIN_SUPERVISOR")
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    ErrorEnvelope envelope = ErrorEnvelope.of(
                            "AUTHENTICATION_REQUIRED",
                            "Avtorizatsiyadan oʻtish talab etiladi",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), envelope);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    ErrorEnvelope envelope = ErrorEnvelope.of(
                            "INSUFFICIENT_PERMISSIONS",
                            "Kirish huquqi yetarli emas",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), envelope);
                })
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Replaces default in-memory user generation to eliminate generated development passwords.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Default UserDetailsService disabled. Use JWT authentication.");
        };
    }
}
