package com.kronos.chiron.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
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

    @Column(name = "poids_corps")
    private Double poidsCorps;

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
