package com.kronos.chiron.noctua.service.impl;

import com.kronos.chiron.noctua.model.NoctuaBriefingType;
import com.kronos.chiron.noctua.service.NoctuaBriefingService;
import com.kronos.chiron.noctua.service.NoctuaVeilleService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NoctuaVeilleServiceImpl implements NoctuaVeilleService {

    private static final int REVEIL_DELAI_MINUTES = 10;
    private static final int ACTIVITE_FENETRE_HEURES = 24;
    private static final int COUCHER_AVANCE_MINUTES = 45;
    private static final int COUCHER_HISTORIQUE_NUITS = 14;
    private static final LocalTime COUCHER_MIN = LocalTime.of(20, 0);
    private static final LocalTime COUCHER_MAX = LocalTime.of(23, 30);
    private static final LocalTime COUCHER_DEFAUT = LocalTime.of(21, 30);

    private static final DateTimeFormatter TITRE_DATE = DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE);
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRANCE);

    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteActiviteRepository santeActiviteRepository;
    private final NoctuaBriefingService noctuaBriefingService;
    private final Clock clock;

    @Override
    public void evaluer(Utilisateur user) {
        evaluerReveil(user);
        evaluerActivite(user);
        evaluerCoucher(user);
    }

    private void evaluerReveil(Utilisateur user) {
        LocalDate today = LocalDate.now(clock);
        for (LocalDate date : List.of(today, today.minusDays(1))) {
            String cle = "REVEIL:" + date;
            if (noctuaBriefingService.dejaProduit(user, cle)) continue;

            Optional<SanteSommeil> nuitOpt = santeSommeilRepository
                    .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, date);
            if (nuitOpt.isEmpty()) continue;
            SanteSommeil nuit = nuitOpt.get();
            if (nuit.getFin() == null || nuit.getScore() == null) continue;
            if (nuit.getFin().plusMinutes(REVEIL_DELAI_MINUTES).isAfter(LocalDateTime.now(clock))) continue;

            noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.REVEIL, date, cle,
                    "Réveil — " + date.format(TITRE_DATE),
                    "Réveil — " + LocalDateTime.now(clock).format(HEURE),
                    "L'utilisateur vient de se réveiller d'une nuit datée du " + date
                            + " (AAAA-MM-JJ). Fais le briefing de réveil en appelant les outils avec cette"
                            + " date, pas la date du jour.");
        }
    }

    private void evaluerActivite(Utilisateur user) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SanteActivite> recentes = santeActiviteRepository
                .findByUtilisateurAndStatutEnrichissementAndEndTimeBetweenOrderByEndTimeDesc(user,
                        StatutEnrichissement.COMPLET, now.minusHours(ACTIVITE_FENETRE_HEURES), now);
        for (SanteActivite activite : recentes) {
            String cle = "ACTIVITE:" + activite.getId();
            if (noctuaBriefingService.dejaProduit(user, cle)) continue;

            noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.ACTIVITE,
                    activite.getStartTime().toLocalDate(), cle,
                    "Activité — " + activite.getStartTime().toLocalDate().format(TITRE_DATE),
                    "Fin de séance — " + now.format(HEURE),
                    "L'utilisateur vient de terminer une activité physique du " + activite.getStartTime().toLocalDate()
                            + " (identifiant " + activite.getId() + "). Fais le briefing d'activité.");
        }
    }

    private void evaluerCoucher(Utilisateur user) {
        LocalDate today = LocalDate.now(clock);
        String cle = "COUCHER:" + today;
        if (noctuaBriefingService.dejaProduit(user, cle)) return;

        LocalTime heureHabituelle = heureCoucherHabituelle(user, today);
        LocalDateTime seuil = today.atTime(heureHabituelle).minusMinutes(COUCHER_AVANCE_MINUTES);
        if (LocalDateTime.now(clock).isBefore(seuil)) return;

        noctuaBriefingService.genererSiNecessaire(user, NoctuaBriefingType.COUCHER, today, cle,
                "Coucher — " + today.format(TITRE_DATE),
                "Coucher — " + LocalDateTime.now(clock).format(HEURE),
                "C'est bientôt l'heure de se coucher, nous sommes le " + today
                        + " (AAAA-MM-JJ). Fais le briefing du coucher.");
    }

    private LocalTime heureCoucherHabituelle(Utilisateur user, LocalDate today) {
        List<SanteSommeil> nuits = santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user,
                today.minusDays(COUCHER_HISTORIQUE_NUITS), today.minusDays(1));
        List<LocalTime> heures = nuits.stream()
                .filter(n -> !n.isSieste())
                .map(n -> n.getDebut().toLocalTime())
                .sorted()
                .toList();
        if (heures.isEmpty()) return COUCHER_DEFAUT;

        LocalTime mediane = heures.size() % 2 == 1
                ? heures.get(heures.size() / 2)
                : heures.get(heures.size() / 2 - 1);
        if (mediane.isBefore(COUCHER_MIN)) return COUCHER_MIN;
        if (mediane.isAfter(COUCHER_MAX)) return COUCHER_MAX;
        return mediane;
    }
}
