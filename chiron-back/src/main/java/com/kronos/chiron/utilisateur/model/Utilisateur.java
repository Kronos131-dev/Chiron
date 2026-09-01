package com.kronos.chiron.utilisateur.model;

import com.kronos.chiron.seance.model.Seance;

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

    @Column(unique = true, nullable = false)
    private String username;

    private String password;

    @Column(name = "icon")
    @Builder.Default
    private String icon = "default_icon.png";

    @Column(name = "rank")
    @Builder.Default
    private String rank = "Citoyen";

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER;

    @Column(unique = true)
    private String email;

    @Column(name = "prenom", length = 100)
    private String prenom;

    @Column(name = "nom", length = 100)
    private String nom;

    @Column(name = "poids_corps")
    private Double poidsCorps;

    @Column(name = "poids_haltere_par_implement", nullable = false)
    @Builder.Default
    private boolean poidsHaltereParImplement = true;

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "utilisateur_materiel", joinColumns = @JoinColumn(name = "utilisateur_id"))
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

    @Column(name = "fitbit_scope", columnDefinition = "TEXT")
    @JsonIgnore
    private String fitbitScope;

    @Column(name = "fitbit_linked_at")
    @JsonIgnore
    private LocalDateTime fitbitLinkedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_coaches", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "coach_id"))
    @JsonIgnore
    @Builder.Default
    private Set<Utilisateur> coaches = new HashSet<>();

    @ManyToMany(mappedBy = "coaches", fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private Set<Utilisateur> coachedUsers = new HashSet<>();

    @OneToMany(mappedBy = "utilisateur", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Seance> seances = new ArrayList<>();

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

    public void addCoach(Utilisateur coach) {
        this.coaches.add(coach);
        coach.getCoachedUsers().add(this);
    }

    public void removeCoach(Utilisateur coach) {
        this.coaches.remove(coach);
        coach.getCoachedUsers().remove(this);
    }
}
