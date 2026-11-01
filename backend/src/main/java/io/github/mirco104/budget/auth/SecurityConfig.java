package io.github.mirco104.budget.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session cookie and CSRF protection (section 15). Every API call except login and health needs a signed-in user
 * (R15); the login itself is a JSON endpoint in {@link AuthController}.
 */
@Configuration
public class SecurityConfig {

    // Local development only; the postgres profile turns the console off
    @Bean
    @Order(1)
    public SecurityFilterChain h2ConsoleFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // US15: the invited person has no account yet; only an admin creates links
                        .requestMatchers(HttpMethod.POST, "/api/invitations/*/accept").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/invitations").hasRole("ADMIN")
                        .requestMatchers("/api/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // The built frontend (AP 19)
                        .anyRequest().permitAll())
                // XSRF-TOKEN cookie, sent back by the frontend as X-XSRF-TOKEN on writing calls
                .csrf(csrf -> csrf.spa())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) -> writeProblem(response,
                                HttpStatus.UNAUTHORIZED, "Nicht angemeldet", "Bitte melde dich an."))
                        .accessDeniedHandler((request, response, e) -> writeProblem(response,
                                HttpStatus.FORBIDDEN, "Nicht erlaubt",
                                "Die Anfrage ist nicht erlaubt. Bitte lade die Seite neu.")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    // RFC 7807 like the other errors; fixed texts only, so no escaping is needed
    private static void writeProblem(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(title, status.value(), detail));
    }
}
