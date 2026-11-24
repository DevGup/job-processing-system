package com.example.Job.service;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record JobCaller(long accountId, boolean admin) {

    public static JobCaller from(JwtAuthenticationToken authentication) {
        long accountId = Long.parseLong(authentication.getToken().getSubject());
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new JobCaller(accountId, admin);
    }

    public boolean canAccess(Long ownerId) {
        return admin || (ownerId != null && ownerId == accountId);
    }
}
