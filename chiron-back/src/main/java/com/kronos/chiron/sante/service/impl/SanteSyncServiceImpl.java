package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.GoogleHealthDataType;
import com.kronos.chiron.fitbit.client.GoogleHealthParser;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.SanteSyncState;
import com.kronos.chiron.sante.model.StatutSync;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.ChargeCardioService;
import com.kronos.chiron.sante.service.ScoreSommeilService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SanteSyncServiceImpl implements SanteSyncService {

    private static final int BACKFILL_JOURS = 90;
    private static final int TRANCHE_JOURS = 14;
    private static final int MAX_PAGES = 50;
    private static final int FENETRE_FC_SECONDES = 300;
    private static final String[] CANDIDATS_ROLLUP = {"sum", "value", "kcalSum", "metersSum", "minutesSum",
            "countSum", "count"};
    private static final String[] CANDIDATS_LIST = {"beatsPerMinute", "percentage", "breathsPerMinute", "bpm",
            "value"};

    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final UtilisateurRepository utilisateurRepository;
    private final SanteJourRepository santeJourRepository;
    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    private final SanteSyncStateRepository santeSyncStateRepository;
    private final ScoreSommeilService scoreSommeilService;
    private final ChargeCardioService chargeCardioService;

    private final Clock clock;

    @Async
    @Transactional
    @Override
    public void ensureBackfillAsync(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername).orElse(null);
        if (user == null) return;
        if (!santeSyncStateRepository.findByUtilisateur(user).isEmpty()) return;

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(BACKFILL_JOURS - 1L);
        log.info("SANTE_BACKFILL_DEMARRE user={} depuis={}", chironUsername, from);
        doSync(user, chironUsername, from, today);
        log.info("SANTE_BACKFILL_TERMINE user={}", chironUsername);
    }

    @Transactional
    @Override
    public void syncRecent(String chironUsername, int jours) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername).orElse(null);
        if (user == null) return;
        int n = Math.max(1, Math.min(jours, BACKFILL_JOURS));
        LocalDate today = LocalDate.now(clock);
        doSync(user, chironUsername, today.minusDays(n - 1L), today);
    }

    private void doSync(Utilisateur user, String chironUsername, LocalDate from, LocalDate to) {
        String token;
        try {
            token = fitbitService.getValidToken(chironUsername);
        } catch (FitbitService.NotLinkedException | FitbitService.ExpiredException e) {
            return;
        }

        syncDailyRollup(user, token, GoogleHealthDataType.STEPS, from, to,
                (jour, v) -> jour.setPas(v.intValue()));
        syncDailyRollup(user, token, GoogleHealthDataType.DISTANCE, from, to, SanteJour::setDistanceM);
        syncDailyRollup(user, token, GoogleHealthDataType.TOTAL_CALORIES, from, to,
                (jour, v) -> jour.setCaloriesTotales(v.intValue()));
        syncDailyRollup(user, token, GoogleHealthDataType.ACTIVE_ENERGY_BURNED, from, to,
                (jour, v) -> jour.setCaloriesActives(v.intValue()));
        syncDailyRollup(user, token, GoogleHealthDataType.ACTIVE_ZONE_MINUTES, from, to,
                (jour, v) -> jour.setMinutesZoneActive(v.intValue()));
        syncZonesCardiaques(user, token, from, to);
        syncHeartRate(user, token, from, to);
        syncListeSimple(user, token, GoogleHealthDataType.DAILY_RESTING_HEART_RATE, from, to,
                (jour, v) -> jour.setFcRepos(v.intValue()));
        syncListeSimple(user, token, GoogleHealthDataType.DAILY_OXYGEN_SATURATION, from, to, SanteJour::setSpo2Pct);
        syncListeSimple(user, token, GoogleHealthDataType.DAILY_RESPIRATORY_RATE, from, to,
                SanteJour::setFrequenceRespiratoire);
        syncHrv(user, token, from, to);
        syncVo2Max(user, token, from, to);
        syncSleep(user, token, from, to);
        completerFcSommeil(user, from, to);

        chargeCardioService.recalculerPlage(user, from, to);
        scoreSommeilService.recalculerPlage(user, from, to);
    }

    private void syncDailyRollup(Utilisateur user, String token, GoogleHealthDataType type, LocalDate from,
            LocalDate to, BiConsumer<SanteJour, Double> appliquer) {
        try {
            LocalDate curseur = from;
            while (!curseur.isAfter(to)) {
                LocalDate fin = borneTranche(curseur, to);
                JsonNode reponse = fitbitClient.dailyRollUp(token, type, curseur, fin);
                Map<LocalDate, Double> parDate = GoogleHealthParser.dailyRollupByDate(reponse, type.camelCaseField(),
                        CANDIDATS_ROLLUP);
                for (Map.Entry<LocalDate, Double> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    appliquer.accept(jour, e.getValue());
                    santeJourRepository.save(jour);
                }
                curseur = fin.plusDays(1);
            }
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncZonesCardiaques(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.TIME_IN_HEART_RATE_ZONE;
        try {
            LocalDate curseur = from;
            while (!curseur.isAfter(to)) {
                LocalDate fin = borneTranche(curseur, to);
                JsonNode reponse = fitbitClient.dailyRollUp(token, type, curseur, fin);
                Map<LocalDate, GoogleHealthParser.ZoneMinutes> parDate = GoogleHealthParser.zoneMinutesByDate(reponse);
                for (Map.Entry<LocalDate, GoogleHealthParser.ZoneMinutes> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setMinutesZoneBruleuse(e.getValue().bruleuse());
                    jour.setMinutesZoneCardio(e.getValue().cardio());
                    jour.setMinutesZonePic(e.getValue().pic());
                    santeJourRepository.save(jour);
                }
                curseur = fin.plusDays(1);
            }
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncHeartRate(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.HEART_RATE;
        ZoneId zone = clock.getZone();
        try {
            LocalDate jour = from;
            while (!jour.isAfter(to)) {
                Instant debutJour = jour.atStartOfDay(zone).toInstant();
                Instant finJour = jour.plusDays(1).atStartOfDay(zone).toInstant();
                JsonNode reponse = fitbitClient.rollUp(token, type, debutJour, finJour, FENETRE_FC_SECONDES);
                List<GoogleHealthParser.FcBucket> buckets = GoogleHealthParser.heartRateBuckets(reponse);
                for (GoogleHealthParser.FcBucket b : buckets) {
                    LocalDateTime horodatage = LocalDateTime.ofInstant(b.debut(), zone);
                    SanteFrequenceCardiaque fc = santeFrequenceCardiaqueRepository
                            .findByUtilisateurAndHorodatage(user, horodatage)
                            .orElseGet(() -> SanteFrequenceCardiaque.builder().utilisateur(user)
                                    .horodatage(horodatage).build());
                    fc.setFcMin(b.min());
                    fc.setFcMoyenne(b.moyenne());
                    fc.setFcMax(b.max());
                    fc.setNbEchantillons(b.nbEchantillons());
                    santeFrequenceCardiaqueRepository.save(fc);
                }
                majFcJournalieres(user, jour, buckets);
                jour = jour.plusDays(1);
            }
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void majFcJournalieres(Utilisateur user, LocalDate date, List<GoogleHealthParser.FcBucket> buckets) {
        List<Integer> mins = buckets.stream().map(GoogleHealthParser.FcBucket::min).filter(Objects::nonNull).toList();
        List<Integer> maxs = buckets.stream().map(GoogleHealthParser.FcBucket::max).filter(Objects::nonNull).toList();
        List<Double> moyennes = buckets.stream().map(GoogleHealthParser.FcBucket::moyenne).filter(Objects::nonNull)
                .toList();
        if (mins.isEmpty() && maxs.isEmpty() && moyennes.isEmpty()) return;
        SanteJour jour = jourDe(user, date);
        if (!mins.isEmpty()) jour.setFcMin(Collections.min(mins));
        if (!maxs.isEmpty()) jour.setFcMax(Collections.max(maxs));
        if (!moyennes.isEmpty()) {
            jour.setFcMoyenne(moyennes.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        }
        santeJourRepository.save(jour);
    }

    private void syncListeSimple(Utilisateur user, String token, GoogleHealthDataType type, LocalDate from,
            LocalDate to, BiConsumer<SanteJour, Double> appliquer) {
        try {
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, Double> parDate = GoogleHealthParser.dailyDoubleByDate(reponse, type.camelCaseField(),
                        CANDIDATS_LIST);
                for (Map.Entry<LocalDate, Double> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    appliquer.accept(jour, e.getValue());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncHrv(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.DAILY_HEART_RATE_VARIABILITY;
        try {
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, GoogleHealthParser.VfcJour> parDate = GoogleHealthParser.hrvByDate(reponse);
                for (Map.Entry<LocalDate, GoogleHealthParser.VfcJour> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setVfcMs(e.getValue().vfcMs());
                    jour.setVfcSommeilProfondMs(e.getValue().vfcSommeilProfondMs());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncVo2Max(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.DAILY_VO2_MAX;
        try {
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, GoogleHealthParser.Vo2MaxJour> parDate = GoogleHealthParser.vo2MaxByDate(reponse);
                for (Map.Entry<LocalDate, GoogleHealthParser.Vo2MaxJour> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setVo2Max(e.getValue().vo2Max());
                    jour.setNiveauAptitude(e.getValue().niveauAptitude());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncSleep(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.SLEEP;
        ZoneId zone = clock.getZone();
        try {
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(reponse);
                for (GoogleHealthParser.SommeilBrut brut : sessions) {
                    if (brut.debut() == null) continue;
                    LocalDateTime debut = LocalDateTime.ofInstant(brut.debut(), zone);
                    LocalDateTime fin = brut.fin() != null ? LocalDateTime.ofInstant(brut.fin(), zone) : null;
                    SanteSommeil session = santeSommeilRepository.findByUtilisateurAndDebut(user, debut)
                            .orElseGet(() -> SanteSommeil.builder().utilisateur(user).debut(debut).build());
                    session.setDate(brut.date() != null ? brut.date() : debut.toLocalDate());
                    session.setFin(fin);
                    session.setExternalId(brut.externalId());
                    session.setSieste(brut.sieste());
                    session.setStadesDisponibles(brut.stadesDisponibles());
                    session.setMinutesEndormi(brut.minutesEndormi());
                    session.setMinutesEveille(brut.minutesEveille());
                    session.setMinutesAvantEndormissement(brut.minutesAvantEndormissement());
                    session.setMinutesApresReveil(brut.minutesApresReveil());
                    session.setMinutesProfond(brut.minutesProfond());
                    session.setMinutesLeger(brut.minutesLeger());
                    session.setMinutesParadoxal(brut.minutesParadoxal());
                    session.setMinutesAgite(brut.minutesAgite());
                    session.setNbReveils(brut.nbReveils());
                    santeSommeilRepository.save(session);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void completerFcSommeil(Utilisateur user, LocalDate from, LocalDate to) {
        List<SanteSommeil> sessions = santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user,
                from, to);
        for (SanteSommeil session : sessions) {
            if (session.getFin() == null) continue;
            List<SanteFrequenceCardiaque> buckets = santeFrequenceCardiaqueRepository
                    .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, session.getDebut(),
                            session.getFin());
            List<Double> moyennes = buckets.stream().map(SanteFrequenceCardiaque::getFcMoyenne)
                    .filter(Objects::nonNull).toList();
            if (!moyennes.isEmpty()) {
                session.setFcSommeilMoyenne(moyennes.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                santeSommeilRepository.save(session);
            }
        }
    }

    private SanteJour jourDe(Utilisateur user, LocalDate date) {
        return santeJourRepository.findByUtilisateurAndDate(user, date)
                .orElseGet(() -> SanteJour.builder().utilisateur(user).date(date).build());
    }

    private LocalDate borneTranche(LocalDate debut, LocalDate to) {
        LocalDate fin = debut.plusDays(TRANCHE_JOURS - 1L);
        return fin.isAfter(to) ? to : fin;
    }

    private void marquerSucces(Utilisateur user, GoogleHealthDataType type, LocalDate jusqua) {
        SanteSyncState etat = etatDe(user, type);
        etat.setDerniereDateSynchronisee(jusqua);
        etat.setDerniereExecution(LocalDateTime.now(clock));
        etat.setDernierStatut(StatutSync.OK);
        etat.setDernierMessage(null);
        santeSyncStateRepository.save(etat);
    }

    private void marquerEchec(Utilisateur user, GoogleHealthDataType type, RuntimeException e) {
        StatutSync statut = classifier(e);
        SanteSyncState etat = etatDe(user, type);
        etat.setDerniereExecution(LocalDateTime.now(clock));
        etat.setDernierStatut(statut);
        etat.setDernierMessage(e.getMessage());
        santeSyncStateRepository.save(etat);
        log.warn("SANTE_SYNC_ECHEC user={} type={} statut={} : {}", user.getUsername(), type, statut,
                e.getMessage());
    }

    private StatutSync classifier(RuntimeException e) {
        if (e instanceof FitbitClient.FitbitUnauthorizedException) return StatutSync.NON_AUTORISE;
        if (e.getMessage() != null && e.getMessage().contains("Quota")) return StatutSync.QUOTA;
        return StatutSync.INDISPONIBLE;
    }

    private SanteSyncState etatDe(Utilisateur user, GoogleHealthDataType type) {
        return santeSyncStateRepository.findByUtilisateurAndTypeDonnee(user, type.name())
                .orElseGet(() -> SanteSyncState.builder().utilisateur(user).typeDonnee(type.name())
                        .dernierStatut(StatutSync.INDISPONIBLE).build());
    }
}
