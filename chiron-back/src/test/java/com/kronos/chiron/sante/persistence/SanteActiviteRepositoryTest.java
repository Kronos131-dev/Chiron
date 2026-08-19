package com.kronos.chiron.sante.persistence;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SanteActiviteRepositoryTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private SanteActiviteRepository santeActiviteRepository;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder()
                .username("athlete")
                .password("pass")
                .role(Role.USER)
                .isPublic(false)
                .build();
        em.persist(user);
        em.flush();
    }

    private SanteActivite makeActivite(Seance seance, SourceActivite source, LocalDateTime start,
            LocalDateTime end, StatutEnrichissement statut, LocalDateTime prochaineTentativeAt) {
        SanteActivite activite = SanteActivite.builder()
                .utilisateur(user)
                .seance(seance)
                .source(source)
                .typeActivite(source == SourceActivite.CHIRON_MUSCU ? TypeActivite.MUSCULATION : TypeActivite.MARCHE)
                .startTime(start)
                .endTime(end)
                .statutEnrichissement(statut)
                .prochaineTentativeAt(prochaineTentativeAt)
                .build();
        em.persist(activite);
        em.flush();
        return activite;
    }

    private Seance makeSeance() {
        Seance s = new Seance();
        s.setTitre("Push Day");
        s.setHistorique(true);
        s.setStartTime(LocalDateTime.now());
        s.setEndTime(LocalDateTime.now());
        s.setUtilisateur(user);
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    void findBySeanceId_returnsLinkedActivite() {
        Seance seance = makeSeance();
        makeActivite(seance, SourceActivite.CHIRON_MUSCU, LocalDateTime.now().minusHours(1), LocalDateTime.now(),
                StatutEnrichissement.COMPLET, null);

        Optional<SanteActivite> result = santeActiviteRepository.findBySeanceId(seance.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSource()).isEqualTo(SourceActivite.CHIRON_MUSCU);
    }

    @Test
    void findBySeanceId_noMatch_returnsEmpty() {
        Optional<SanteActivite> result = santeActiviteRepository.findBySeanceId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUtilisateurAndStartTimeBetween_ordersByStartTimeDesc() {
        LocalDateTime today = LocalDateTime.now();
        makeActivite(null, SourceActivite.GOOGLE_DETECTE, today.minusDays(2), today.minusDays(2).plusMinutes(30),
                StatutEnrichissement.COMPLET, null);
        makeActivite(null, SourceActivite.GOOGLE_DETECTE, today.minusDays(1), today.minusDays(1).plusMinutes(30),
                StatutEnrichissement.COMPLET, null);

        List<SanteActivite> result = santeActiviteRepository
                .findByUtilisateurAndStartTimeBetweenOrderByStartTimeDesc(user, today.minusDays(5), today);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStartTime()).isAfter(result.get(1).getStartTime());
    }

    @Test
    void findByStatutEnrichissementAndProchaineTentativeAtLessThanEqual_returnsOnlyDueRetries() {
        LocalDateTime now = LocalDateTime.now();
        makeActivite(null, SourceActivite.CHIRON_MUSCU, now.minusHours(2), now.minusHours(1),
                StatutEnrichissement.EN_ATTENTE, now.minusMinutes(1));
        makeActivite(null, SourceActivite.CHIRON_MUSCU, now.minusHours(5), now.minusHours(4),
                StatutEnrichissement.EN_ATTENTE, now.plusMinutes(30));
        makeActivite(null, SourceActivite.CHIRON_MUSCU, now.minusHours(8), now.minusHours(7),
                StatutEnrichissement.COMPLET, null);

        List<SanteActivite> due = santeActiviteRepository
                .findByStatutEnrichissementAndProchaineTentativeAtLessThanEqual(StatutEnrichissement.EN_ATTENTE, now);

        assertThat(due).hasSize(1);
    }

    @Test
    void uniqueSeanceId_secondActiviteForSameSeance_isRejected() {
        Seance seance = makeSeance();
        makeActivite(seance, SourceActivite.CHIRON_MUSCU, LocalDateTime.now().minusHours(1), LocalDateTime.now(),
                StatutEnrichissement.COMPLET, null);

        SanteActivite doublon = SanteActivite.builder()
                .utilisateur(user).seance(seance).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(LocalDateTime.now().minusHours(1)).endTime(LocalDateTime.now())
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE)
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            em.persist(doublon);
            em.flush();
        }).isInstanceOf(RuntimeException.class);
    }
}
