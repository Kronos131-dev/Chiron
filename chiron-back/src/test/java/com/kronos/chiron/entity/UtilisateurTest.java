package com.kronos.chiron.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UtilisateurTest {

    @Test
    void addCoach_maintainsBidirectionalRelationship() {
        Utilisateur user = Utilisateur.builder().username("athlete").build();
        Utilisateur coach = Utilisateur.builder().username("coach").build();

        user.addCoach(coach);

        assertThat(user.getCoaches()).contains(coach);
        assertThat(coach.getCoachedUsers()).contains(user);
    }

    @Test
    void removeCoach_cleansUpBothSides() {
        Utilisateur user = Utilisateur.builder().username("athlete").build();
        Utilisateur coach = Utilisateur.builder().username("coach").build();

        user.addCoach(coach);
        user.removeCoach(coach);

        assertThat(user.getCoaches()).doesNotContain(coach);
        assertThat(coach.getCoachedUsers()).doesNotContain(user);
    }

    @Test
    void defaultRole_isUser() {
        Utilisateur user = Utilisateur.builder().username("someone").build();
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void defaultIcon_isDefaultPng() {
        Utilisateur user = Utilisateur.builder().username("someone").build();
        assertThat(user.getIcon()).isEqualTo("default_icon.png");
    }

    @Test
    void defaultRank_isCitoyen() {
        Utilisateur user = Utilisateur.builder().username("someone").build();
        assertThat(user.getRank()).isEqualTo("Citoyen");
    }

    @Test
    void getAuthorities_userRole_returnsRoleUser() {
        Utilisateur user = Utilisateur.builder().username("u").role(Role.USER).build();
        var authorities = user.getAuthorities();
        assertThat(authorities).hasSize(1);
        assertThat(authorities.iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void getAuthorities_adminRole_returnsRoleAdmin() {
        Utilisateur user = Utilisateur.builder().username("admin").role(Role.ADMIN).build();
        assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void accountStatusFlags_areAllTrue() {
        Utilisateur user = Utilisateur.builder().username("u").build();
        assertThat(user.isAccountNonExpired()).isTrue();
        assertThat(user.isAccountNonLocked()).isTrue();
        assertThat(user.isCredentialsNonExpired()).isTrue();
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void addCoach_multiple_coachesStoredCorrectly() {
        Utilisateur user = Utilisateur.builder().username("athlete").build();
        Utilisateur coach1 = Utilisateur.builder().username("coach1").build();
        Utilisateur coach2 = Utilisateur.builder().username("coach2").build();

        user.addCoach(coach1);
        user.addCoach(coach2);

        assertThat(user.getCoaches()).containsExactlyInAnyOrder(coach1, coach2);
    }
}
