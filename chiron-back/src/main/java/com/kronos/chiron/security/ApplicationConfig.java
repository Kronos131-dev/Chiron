package com.kronos.chiron.security;

import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Global application configuration class for security components.
 * This class provisions beans required for Spring Security's authentication
 * process, including the user details service, password encoder, and
 * authentication providers.
 */
@Configuration
@RequiredArgsConstructor
public class ApplicationConfig {

    private final UtilisateurRepository repository;

    /**
     * Provides a custom {@link UserDetailsService} that retrieves users from the database.
     *
     * @return A UserDetailsService instance that fetches users by their username.
     * @throws UsernameNotFoundException if the specified username does not exist in the database.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Configures the {@link AuthenticationProvider} which is responsible for verifying user credentials.
     * It connects the custom {@link UserDetailsService} and {@link PasswordEncoder} to the authentication process.
     *
     * @param userDetailsService The service used to fetch user details.
     * @param passwordEncoder    The service used to encode and verify passwords.
     * @return A fully configured AuthenticationProvider (specifically, a DaoAuthenticationProvider).
     */
    @Bean
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * Exposes the Spring Security {@link AuthenticationManager} as a Bean so it can be injected
     * into other components, such as authentication controllers.
     *
     * @param config The authentication configuration provided by Spring Security.
     * @return The configured AuthenticationManager.
     * @throws Exception If an error occurs while retrieving the AuthenticationManager.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Configures the default {@link PasswordEncoder} for the application.
     * This encoder is used to hash passwords before storing them and to verify
     * incoming password hashes during authentication.
     *
     * @return A BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
