package com.kronos.chiron.repository;

import com.kronos.chiron.entity.Role;
import com.kronos.chiron.entity.Utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UtilisateurRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private UtilisateurRepository repository;

    private Utilisateur persist(String username) {
        Utilisateur u = Utilisateur.builder()
                .username(username)
                .password("hash")
                .role(Role.USER)
                .isPublic(false)
                .build();
        em.persist(u);
        em.flush();
        return u;
    }

    @Test
    void findByUsername_existing_returnsUser() {
        persist("alice");
        Optional<Utilisateur> result = repository.findByUsername("alice");
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("alice");
    }

    @Test
    void findByUsername_nonExistent_returnsEmpty() {
        Optional<Utilisateur> result = repository.findByUsername("ghost");
        assertThat(result).isEmpty();
    }

    @Test
    void findByUsernameContainingIgnoreCase_partialMatch() {
        persist("alice");
        persist("alicia");
        persist("bob");

        List<Utilisateur> results = repository.findByUsernameContainingIgnoreCase("ali");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Utilisateur::getUsername)
                .containsExactlyInAnyOrder("alice", "alicia");
    }

    @Test
    void findByUsernameContainingIgnoreCase_caseInsensitive() {
        persist("Alice");

        List<Utilisateur> results = repository.findByUsernameContainingIgnoreCase("alice");

        assertThat(results).hasSize(1);
    }

    @Test
    void findByUsernameContainingIgnoreCase_noMatch_returnsEmptyList() {
        persist("charlie");

        List<Utilisateur> results = repository.findByUsernameContainingIgnoreCase("xyz");

        assertThat(results).isEmpty();
    }

    @Test
    void save_persistsUserWithDefaultValues() {
        Utilisateur u = Utilisateur.builder()
                .username("newuser")
                .password("pass")
                .build();
        Utilisateur saved = repository.save(u);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRank()).isEqualTo("Citoyen");
        assertThat(saved.getIcon()).isEqualTo("default_icon.png");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void findAll_returnsAllUsers() {
        persist("user1");
        persist("user2");
        persist("user3");

        List<Utilisateur> all = repository.findAll();

        assertThat(all.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void delete_removesUser() {
        Utilisateur u = persist("toDelete");
        repository.delete(u);
        em.flush();

        Optional<Utilisateur> result = repository.findByUsername("toDelete");
        assertThat(result).isEmpty();
    }
}
