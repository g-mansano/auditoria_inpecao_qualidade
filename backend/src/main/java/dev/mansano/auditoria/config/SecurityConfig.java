package dev.mansano.auditoria.config;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import dev.mansano.auditoria.seguranca.TokenAuthFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TokenAuthFilter tokenFilter) throws Exception {
        http
                // API stateless com token no header; sem cookie de sessão, CSRF não se aplica.
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .headers(h -> h
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                                "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; " +
                                "frame-ancestors 'none'; form-action 'self'; base-uri 'self';"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicy(p -> p.policy("camera=(self), microphone=(), geolocation=()")))
                .authorizeHttpRequests(a -> a
                        // O ZIP sai por StreamingResponseBody (despacho ASYNC). A permissão já foi conferida
                        // no despacho original; sem isto o 2º despacho cai em Access Denied e a conexão fecha.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/login", "/api/demo", "/actuator/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Front-end Angular (estático) é público; os dados exigem token.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** O filtro roda só dentro da cadeia do Spring Security, não como filtro servlet avulso. */
    @Bean
    FilterRegistrationBean<TokenAuthFilter> tokenFilterRegistration(TokenAuthFilter filter) {
        var reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
