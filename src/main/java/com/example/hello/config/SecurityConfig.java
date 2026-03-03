package com.example.hello.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                // Allow static resources and home page
                .requestMatchers("/", "/index.html", "/error", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                // Allow our public APIs
                .requestMatchers("/api/**").permitAll()
                // Allow local authentication APIs (if they exist)
                .requestMatchers("/login", "/register", "/currentUser").permitAll()
                // Require authentication for all other requests
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/").permitAll()
            )
            .csrf(csrf -> csrf.disable()); // Disabled CSRF for simplicity in demo
        
        return http.build();
    }
}
