package com.kronos.chiron.entity;

import com.kronos.chiron.exercice.model.TypeEquipement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Entity representing a system user.
 * Implements Spring Security's UserDetails interface for authentication and authorization.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique username for authentication and identification.
     */
    @Column(unique = true, nullable = false)
    private String username;

    /**
     * Encrypted password.
     */
    private String password;

    /**
     * Profile icon filename or URL.
     */
    @Column(name = "icon")
    @Builder.Default
    private String icon = "default_icon.png";

    /**
     * The user's current rank or title within the platform.
     */
    @Column(name = "rank")
    @Builder.Default
    private String rank = "Citoyen";

    /**
     * Indicates whether the user's profile is public.
     */
    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    /**
     * The user's primary system role.
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(unique = true)
    private String email;

    /** Prénom de l'utilisateur (sert à identifier le destinataire d'un rapport Visbody). */
    @Column(name = "prenom", length = 100)
    private String prenom;

    /** Nom de l'utilisateur (sert à identifier le destinataire d'un rapport Visbody). */
    @Column(name = "nom", length = 100)
    private String nom;

    @Column(name = "poids_corps")
    private Double poidsCorps;

    /** Aux haltères, le poids saisi est celui d'une seule haltère → tonnage ×2 (défaut true). */
    @Column(name = "poids_haltere_par_implement", nullable = false)
    @Builder.Default
    private boolean poidsHaltereParImplement = true;

    /** Aux machines, le poids saisi est celui d'un seul côté → tonnage ×2 (défaut false). */
    @Column(name = "poids_machine_par_cote", nullable = false)
    @Builder.Default
    private boolean poidsMachineParCote = false;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Enumerated(EnumType.STRING)
    @Column(name = "sexe", length = 16)
    private Sexe sexe;

    @Column(name = "taille_cm")
    private Double tailleCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "niveau_experience", length = 32)
    private NiveauExperience niveauExperience;

    @Enumerated(EnumType.STRING)
    @Column(name = "objectif_principal", length = 32)
    private ObjectifPrincipal objectifPrincipal;

    @Column(name = "frequence_visee")
    private Integer frequenceVisee;

    @Column(name = "blessures", columnDefinition = "TEXT")
    private String blessures;

    @Column(name = "preferences", columnDefinition = "TEXT")
    private String preferences;

    @Column(name = "is_onboarded", nullable = false)
    @Builder.Default
    private Boolean isOnboarded = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_provider", nullable = false, length = 20)
    @Builder.Default
    private AiProvider aiProvider = AiProvider.MISTRAL;

    /** Jour de comptage des requêtes Gemini (réinitialisé quand la date change). */
    @Column(name = "gemini_call_date")
    private LocalDate geminiCallDate;

    /** Nombre de requêtes Gemini consommées pour {@link #geminiCallDate}. */
    @Column(name = "gemini_call_count", nullable = false)
    @Builder.Default
    private int geminiCallCount = 0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "utilisateur_materiel",
            joinColumns = @JoinColumn(name = "utilisateur_id")
    )
    @Column(name = "equipement", length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<TypeEquipement> materielDisponible = EnumSet.noneOf(TypeEquipement.class);

    @Column(name = "olympus_token_encrypted", columnDefinition = "TEXT")
    @JsonIgnore
    private String olympusTokenEncrypted;

    @Column(name = "olympus_token_expires_at")
    @JsonIgnore
    private LocalDateTime olympusTokenExpiresAt;

    @Column(name = "olympus_username")
    @JsonIgnore
    private String olympusUsername;

    @Column(name = "olympus_linked_at")
    @JsonIgnore
    private LocalDateTime olympusLinkedAt;

    // Liaison Fitbit (OAuth2). Tokens chiffrés AES-256-GCM via TokenCipherService.
    @Column(name = "fitbit_access_token_encrypted", columnDefinition = "TEXT")
    @JsonIgnore
    private String fitbitAccessTokenEncrypted;

    @Column(name = "fitbit_refresh_token_encrypted", columnDefinition = "TEXT")
    @JsonIgnore
    private String fitbitRefreshTokenEncrypted;

    @Column(name = "fitbit_token_expires_at")
    @JsonIgnore
    private LocalDateTime fitbitTokenExpiresAt;

    @Column(name = "fitbit_user_id", length = 64)
    @JsonIgnore
    private String fitbitUserId;

    @Column(name = "fitbit_scope")
    @JsonIgnore
    private String fitbitScope;

    @Column(name = "fitbit_linked_at")
    @JsonIgnore
    private LocalDateTime fitbitLinkedAt;

    /**
     * The set of users who act as coaches for this user.
     * These coaches have access to this user's programs.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_coaches",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "coach_id")
    )
    @JsonIgnore
    @Builder.Default
    private Set<Utilisateur> coaches = new HashSet<>();

    /**
     * The set of users whom this user coaches.
     * Ignored during JSON serialization to prevent infinite recursion.
     */
    @ManyToMany(mappedBy = "coaches", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<Utilisateur> coachedUsers = new HashSet<>();

    /**
     * The list of workout sessions owned by this user.
     */
    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Seance> seances = new ArrayList<>();

    /**
     * Returns the authorities granted to the user based on their role.
     *
     * @return A collection of granted authorities.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == null) {
            this.role = Role.USER;
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Adds a coach to this user's list of coaches.
     * Maintains the bidirectional many-to-many relationship.
     *
     * @param coach The user to be added as a coach.
     */
    public void addCoach(Utilisateur coach) {
        this.coaches.add(coach);
        coach.getCoachedUsers().add(this);
    }

    /**
     * Removes a coach from this user's list of coaches.
     * Maintains the bidirectional many-to-many relationship.
     *
     * @param coach The user to be removed as a coach.
     */
    public void removeCoach(Utilisateur coach) {
        this.coaches.remove(coach);
        coach.getCoachedUsers().remove(this);
    }
}
