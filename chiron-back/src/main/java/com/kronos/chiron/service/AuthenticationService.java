package com.kronos.chiron.service;

import com.kronos.chiron.dto.auth.*;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Service responsible for user registration and authentication logic.
 * Handles password encryption, JWT generation, and role assignment.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {
    
    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new user in the system.
     * Automatically assigns the ADMIN role to specific predefined usernames.
     *
     * @param request The registration request containing user details.
     * @return An AuthenticationResponse containing the generated JWT token.
     */
    public AuthenticationResponse register(RegisterRequest request) {
        Role userRole = Role.USER;
        
        if ("kronos".equalsIgnoreCase(request.username()) || "chiron".equalsIgnoreCase(request.username())) {
            userRole = Role.ADMIN;
        }

        var user = Utilisateur.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .isPublic(false)
                .role(userRole)
                .build();
                
        repository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return new AuthenticationResponse(jwtToken);
    }

    /**
     * Authenticates an existing user based on their credentials.
     * Generates a new JWT token upon successful authentication.
     * Ensures predefined admin accounts are correctly upgraded if their roles were missing.
     *
     * @param request The authentication request containing the username and password.
     * @return An AuthenticationResponse containing the newly generated JWT token.
     */
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        
        var user = repository.findByUsername(request.username()).orElseThrow();
        
        if (("kronos".equalsIgnoreCase(user.getUsername()) || "chiron".equalsIgnoreCase(user.getUsername())) && user.getRole() != Role.ADMIN) {
            user.setRole(Role.ADMIN);
            repository.save(user);
        }

        var jwtToken = jwtService.generateToken(user);
        return new AuthenticationResponse(jwtToken);
    }
}
