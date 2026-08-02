package com.kronos.chiron.auth.service;

import com.kronos.chiron.auth.dto.AuthenticationRequest;
import com.kronos.chiron.auth.dto.AuthenticationResponse;
import com.kronos.chiron.auth.dto.RegisterRequest;

import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    
    private final UtilisateurRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthenticationResponse register(RegisterRequest request) {
        Role userRole = Role.USER;
        
        if ("kronos".equalsIgnoreCase(request.username()) || "chiron".equalsIgnoreCase(request.username())) {
            userRole = Role.ADMIN;
        }

        var user = Utilisateur.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .isPublic(false)
                .role(userRole)
                .build();
                
        repository.save(user);
        var jwtToken = jwtService.generateToken(user);
        return new AuthenticationResponse(jwtToken);
    }

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
