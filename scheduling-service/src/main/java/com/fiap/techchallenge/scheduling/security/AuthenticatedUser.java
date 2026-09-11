package com.fiap.techchallenge.scheduling.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * UserDetails implementation that also carries the linked patientId (nullable
 * for MEDICO/ENFERMEIRO), needed to enforce "a patient can only see their own
 * data" in the service layer.
 */
public class AuthenticatedUser extends User {

    private final Long patientId;

    public AuthenticatedUser(String username, String password, String role, Long patientId) {
        super(username, password, authorities(role));
        this.patientId = patientId;
    }

    private static List<GrantedAuthority> authorities(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public Long getPatientId() {
        return patientId;
    }
}
