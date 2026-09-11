package com.fiap.techchallenge.scheduling.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Small helper to read the authenticated principal from the security context,
 * used by the service layer to enforce ownership checks that go beyond what
 * {@code @PreAuthorize} on roles alone can express (e.g. "this patient").
 */
@Component
public class CurrentUser {

    public AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("Nenhum usuario autenticado no contexto de seguranca");
        }
        return user;
    }
}
