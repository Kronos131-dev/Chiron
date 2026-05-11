package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for managing Utilisateur (User) entities.
 * Provides basic CRUD operations and custom queries based on usernames.
 */
@Repository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    /**
     * Retrieves a user by their exact, unique username.
     *
     * @param username The exact username to search for.
     * @return An Optional containing the found Utilisateur, or empty if not found.
     */
    Optional<Utilisateur> findByUsername(String username);

    /**
     * Finds a list of users whose usernames contain the given string (case-insensitive).
     * Useful for search or autocomplete features.
     *
     * @param username The partial username to search for.
     * @return A list of matching Utilisateur entities.
     */
    List<Utilisateur> findByUsernameContainingIgnoreCase(String username);
}
