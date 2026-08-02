package com.kronos.chiron.core.security;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.kronos.chiron.core.exceptions.ErrorFactory.forbidden;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import static com.kronos.chiron.core.exceptions.ErrorFactory.unauthorized;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthenticatedUserService {

    private final UtilisateurRepository utilisateurRepository;

    public String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null) {
            throw unauthorized("Aucun utilisateur authentifié");
        }
        return authentication.getName();
    }

    public Utilisateur getAuthenticatedUser() {
        String username = getAuthenticatedUsername();
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("utilisateur", username));
    }

    public void requireSelf(String username) {
        if (!getAuthenticatedUsername().equalsIgnoreCase(username)) {
            throw forbidden("Accès refusé aux données d'un autre utilisateur");
        }
    }

    public boolean isAuthenticatedAs(String username) {
        return username != null && getAuthenticatedUsername().equalsIgnoreCase(username);
    }
}
