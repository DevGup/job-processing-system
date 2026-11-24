package com.example.Job.auth;

import com.example.Job.config.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AuthService implements ApplicationRunner {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final String adminUsername;
    private final String adminPassword;
    private final String dummyHash;

    AuthService(AccountRepository accounts, PasswordEncoder passwords, JwtEncoder tokens,
                @Value("${app.auth.admin-username}") String adminUsername,
                @Value("${app.auth.admin-password}") String adminPassword) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        if (!USERNAME.matcher(adminUsername).matches()) {
            throw new IllegalStateException("JOB_API_USERNAME must use 1-64 ASCII letters, digits, dots, underscores or hyphens");
        }
        this.adminUsername = adminUsername.toLowerCase(Locale.ROOT);
        this.adminPassword = adminPassword;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Override
    public void run(ApplicationArguments args) {
        Account admin = accounts.findByUsername(adminUsername).orElse(null);
        if (admin == null) {
            if (!bcryptAccepts(adminPassword)) {
                throw new IllegalStateException("JOB_API_PASSWORD must be nonblank, at most 72 UTF-8 bytes, and contain no NUL");
            }
            try {
                accounts.saveAndFlush(new Account(adminUsername, passwords.encode(adminPassword), Account.Role.ADMIN));
                return;
            } catch (DataIntegrityViolationException conflict) {
                admin = accounts.findByUsername(adminUsername).orElseThrow(() -> conflict);
            }
        }
        if (admin.role() != Account.Role.ADMIN) {
            throw new IllegalStateException("Configured bootstrap username already belongs to a regular account");
        }
    }

    Account register(String username, String password) {
        if (username == null || !USERNAME.matcher(username).matches() || !bcryptAccepts(password)
                || password.codePointCount(0, password.length()) < 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username or password format");
        }
        String normalized = username.toLowerCase(Locale.ROOT);
        if (normalized.equals(adminUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username unavailable");
        }
        try {
            return accounts.saveAndFlush(new Account(normalized, passwords.encode(password), Account.Role.USER));
        } catch (DataIntegrityViolationException conflict) {
            if (accounts.findByUsername(normalized).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username unavailable");
            }
            throw conflict;
        }
    }

    String login(String username, String password) {
        if (username == null || !USERNAME.matcher(username).matches() || !bcryptAccepts(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        Account account = accounts.findByUsername(username.toLowerCase(Locale.ROOT)).orElse(null);
        if (!passwords.matches(password, account == null ? dummyHash : account.passwordHash()) || account == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.JWT_ISSUER)
                .subject(account.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(TOKEN_LIFETIME))
                .claim("roles", List.of(account.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return tokens.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static boolean bcryptAccepts(String password) {
        return password != null && password.length() <= 72 && !password.isBlank() && password.indexOf('\0') < 0
                && StandardCharsets.UTF_8.newEncoder().canEncode(password)
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
