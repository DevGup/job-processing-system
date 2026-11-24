package com.example.Job.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {
    private final AuthService auth;

    AuthController(AuthService auth) {
        this.auth = auth;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Credentials(String username, String password) {}

    record RegisteredAccount(Long id, String username, String role) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredAccount register(@RequestBody Credentials credentials) {
        Account account = auth.register(credentials.username(), credentials.password());
        return new RegisteredAccount(account.id(), account.username(), account.role().name());
    }

    @PostMapping("/login")
    ResponseEntity<Map<String, String>> login(@RequestBody Credentials credentials) {
        String token = auth.login(credentials.username(), credentials.password());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("accessToken", token));
    }
}
