package com.kronos.chiron.service;

import com.kronos.chiron.dto.auth.AuthenticationRequest;
import com.kronos.chiron.dto.auth.AuthenticationResponse;
import com.kronos.chiron.dto.auth.RegisterRequest;
import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    @Mock private UtilisateurRepository repository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn("encoded-password");
        when(jwtService.generateToken(any())).thenReturn("fake-token");
    }

    @Test
    void register_normalUser_assignsUserRole() {
        RegisterRequest request = new RegisterRequest("alice", "alice@test.com", "pass");

        authenticationService.register(request);

        verify(repository).save(argThat(user ->
                user.getRole() == Role.USER && user.getUsername().equals("alice")
        ));
    }

    @Test
    void register_usernameKronos_assignsAdminRole() {
        RegisterRequest request = new RegisterRequest("kronos", "k@test.com", "pass");

        authenticationService.register(request);

        verify(repository).save(argThat(user -> user.getRole() == Role.ADMIN));
    }

    @Test
    void register_usernameChiron_assignsAdminRole() {
        RegisterRequest request = new RegisterRequest("chiron", "c@test.com", "pass");

        authenticationService.register(request);

        verify(repository).save(argThat(user -> user.getRole() == Role.ADMIN));
    }

    @Test
    void register_usernameCaseInsensitive_adminForKRONOS() {
        RegisterRequest request = new RegisterRequest("KRONOS", "k@test.com", "pass");

        authenticationService.register(request);

        verify(repository).save(argThat(user -> user.getRole() == Role.ADMIN));
    }

    @Test
    void register_returnsToken() {
        RegisterRequest request = new RegisterRequest("bob", "bob@test.com", "pass");

        AuthenticationResponse response = authenticationService.register(request);

        assertThat(response.token()).isEqualTo("fake-token");
    }

    @Test
    void register_encodesPassword() {
        RegisterRequest request = new RegisterRequest("carol", "carol@test.com", "plaintext");

        authenticationService.register(request);

        verify(passwordEncoder).encode("plaintext");
        verify(repository).save(argThat(user -> user.getPassword().equals("encoded-password")));
    }

    @Test
    void authenticate_validCredentials_returnsToken() {
        Utilisateur user = Utilisateur.builder()
                .username("dave")
                .role(Role.USER)
                .build();
        when(repository.findByUsername("dave")).thenReturn(Optional.of(user));

        AuthenticationResponse response = authenticationService.authenticate(
                new AuthenticationRequest("dave", "pass"));

        assertThat(response.token()).isEqualTo("fake-token");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void authenticate_kronosWithoutAdminRole_upgradesRole() {
        Utilisateur user = Utilisateur.builder()
                .username("kronos")
                .role(Role.USER)
                .build();
        when(repository.findByUsername("kronos")).thenReturn(Optional.of(user));

        authenticationService.authenticate(new AuthenticationRequest("kronos", "pass"));

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(repository).save(user);
    }

    @Test
    void authenticate_alreadyAdmin_doesNotCallSave() {
        Utilisateur user = Utilisateur.builder()
                .username("normaluser")
                .role(Role.USER)
                .build();
        when(repository.findByUsername("normaluser")).thenReturn(Optional.of(user));

        authenticationService.authenticate(new AuthenticationRequest("normaluser", "pass"));

        verify(repository, never()).save(any());
    }
}
