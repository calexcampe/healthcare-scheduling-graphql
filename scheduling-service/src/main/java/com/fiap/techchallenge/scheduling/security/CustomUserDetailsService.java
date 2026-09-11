package com.fiap.techchallenge.scheduling.security;

import com.fiap.techchallenge.scheduling.domain.Patient;
import com.fiap.techchallenge.scheduling.domain.User;
import com.fiap.techchallenge.scheduling.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + username));

        Patient patient = user.getPatient();
        Long patientId = patient != null ? patient.getId() : null;

        return new AuthenticatedUser(user.getUsername(), user.getPassword(), user.getRole().name(), patientId);
    }
}
