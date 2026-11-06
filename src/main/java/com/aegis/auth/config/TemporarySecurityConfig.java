package com.aegis.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * TEMPORARY, UNSAFE: lets every request through so the team can test the
 * pipeline in Postman. (owner: BB)
 *
 * BB: DELETE this file when the real JWT security config is ready.
 * This must NEVER reach the final demo.
 */
@Configuration
public class TemporarySecurityConfig {

    @Bean
    public SecurityFilterChain openFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
