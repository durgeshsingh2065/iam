package com.iam.assistant.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final McpServerProperties mcpServerProperties;

    public SecurityConfig(McpServerProperties mcpServerProperties) {
        this.mcpServerProperties = mcpServerProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(apiKeyFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/mcp/**").authenticated()
                //.requestMatchers("/api/reports/**").authenticated()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    public ApiKeyAuthFilter apiKeyFilter() {
        return new ApiKeyAuthFilter(mcpServerProperties.getApiKey());
    }
}
