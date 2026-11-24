package com.example.Job.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Objects;

@Configuration
public class SecurityConfig {

    public static final String JWT_ISSUER = "job-processing-system";

    @Bean
    SecretKey jwtKey(@Value("${app.auth.jwt-secret}") String encodedSecret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JOB_JWT_SECRET must be Base64 encoded");
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("JOB_JWT_SECRET must decode to at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(JWT_ISSUER),
                new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<>(JwtClaimNames.SUB, SecurityConfig::validAccountId)));
        return decoder;
    }

    private static boolean validAccountId(String subject) {
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter authentication = new JwtAuthenticationConverter();
        authentication.setJwtGrantedAuthoritiesConverter(roles);

        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login").permitAll()
                .requestMatchers(HttpMethod.PUT, "/jobs/*/status").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/jobs/retry-dlq/*").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(server -> server.jwt(jwt -> jwt.jwtAuthenticationConverter(authentication)));

        return http.build();
    }
}
