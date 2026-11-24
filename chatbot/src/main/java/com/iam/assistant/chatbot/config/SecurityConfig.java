package com.iam.assistant.chatbot.config;

import com.iam.assistant.chatbot.service.UserManagementService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserManagementService userManagementService;
    private final PasswordEncoder passwordEncoder;

    public SecurityConfig(UserManagementService userManagementService,
                          PasswordEncoder passwordEncoder) {
        this.userManagementService = userManagementService;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userManagementService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(authenticationProvider());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error").permitAll()
                .requestMatchers("/admin", "/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/chat", "/chat/**", "/sessions", "/sessions/**",
                        "/api/user/**")
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    String acceptHeader = request.getHeader("Accept");
                    String requestedWith = request.getHeader("X-Requested-With");
                    boolean isAjax = "XMLHttpRequest".equals(requestedWith)
                            || (acceptHeader != null && acceptHeader.contains("application/json"));
                    if (isAjax) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write(
                            "{\"error\":true,\"errorMessage\":\"Please log in to continue.\"}");
                    } else {
                        response.sendRedirect("/login?required=true");
                    }
                })
            );
        return http.build();
    }
}
