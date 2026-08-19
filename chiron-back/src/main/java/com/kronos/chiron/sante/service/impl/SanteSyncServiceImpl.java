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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SanteSyncServiceImpl implements SanteSyncService {

    private static final int BACKFILL_JOURS = 90;
    private static final int TRANCHE_JOURS = 14;
    private static final int MAX_PAGES = 50;
    private static final int FENETRE_FC_SECONDES = 300;
    private static final double MILLIMETRES_PAR_METRE = 1000.0;
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
        Set<GoogleHealthDataType> aRattraper = typesSansBackfill(user);
        if (aRattraper.isEmpty()) return;

        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(BACKFILL_JOURS - 1L);
        log.info("SANTE_BACKFILL_DEMARRE user={} depuis={} types={}", chironUsername, from, aRattraper.size());
        doSync(user, chironUsername, from, today, aRattraper);
        marquerBackfillTermine(user, aRattraper);
        log.info("SANTE_BACKFILL_TERMINE user={}", chironUsername);
    }

    private Set<GoogleHealthDataType> typesSansBackfill(Utilisateur user) {
        Map<String, SanteSyncState> parType = santeSyncStateRepository.findByUtilisateur(user).stream()
                .collect(Collectors.toMap(SanteSyncState::getTypeDonnee, Function.identity(), (a, b) -> a));
        Set<GoogleHealthDataType> resultat = EnumSet.noneOf(GoogleHealthDataType.class);
        for (GoogleHealthDataType type : GoogleHealthDataType.values()) {
            SanteSyncState etat = parType.get(type.name());
            if (etat == null || !etat.isBackfillTermine()) resultat.add(type);
        }
        return resultat;
    }

    private void marquerBackfillTermine(Utilisateur user, Set<GoogleHealthDataType> types) {
        for (GoogleHealthDataType type : types) {
            santeSyncStateRepository.findByUtilisateurAndTypeDonnee(user, type.name())
                    .filter(etat -> etat.getDernierStatut() == StatutSync.OK)
                    .ifPresent(etat -> {
                        etat.setBackfillTermine(true);
                        santeSyncStateRepository.save(etat);
                    });
        }
    }

    @Transactional
    @Override
    public void syncRecent(String chironUsername, int jours) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername).orElse(null);
        if (user == null) return;
        int n = Math.max(1, Math.min(jours, BACKFILL_JOURS));
        LocalDate today = LocalDate.now(clock);
        doSync(user, chironUsername, today.minusDays(n - 1L), today,
                EnumSet.allOf(GoogleHealthDataType.class));
    }

    private void doSync(Utilisateur user, String chironUsername, LocalDate from, LocalDate to,
            Set<GoogleHealthDataType> types) {
        String token;
        try {
            token = fitbitService.getValidToken(chironUsername);
        } catch (FitbitService.NotLinkedException | FitbitService.ExpiredException e) {
            return;
        }

        if (types.contains(GoogleHealthDataType.STEPS)) {
            syncDailyRollup(user, token, GoogleHealthDataType.STEPS, from, to,
                    (jour, v) -> jour.setPas(v.intValue()));
        }
        if (types.contains(GoogleHealthDataType.DISTANCE)) {
            // WHY: Google Health renvoie la distance en millimètres, jamais en mètres malgré
            // le nom du champ metersSum. Constaté sur un compte réel : 6 175 600 pour 8 623
            // pas, soit 716 par pas — une foulée en millimètres, absurde en mètres.
            syncDailyRollup(user, token, GoogleHealthDataType.DISTANCE, from, to,
                    (jour, v) -> jour.setDistanceM(v / MILLIMETRES_PAR_METRE));
        }
        if (types.contains(GoogleHealthDataType.TOTAL_CALORIES)) {
            syncDailyRollup(user, token, GoogleHealthDataType.TOTAL_CALORIES, from, to,
                    (jour, v) -> jour.setCaloriesTotales(v.intValue()));
        }
        if (types.contains(GoogleHealthDataType.ACTIVE_ENERGY_BURNED)) {
            syncDailyRollup(user, token, GoogleHealthDataType.ACTIVE_ENERGY_BURNED, from, to,
                    (jour, v) -> jour.setCaloriesActives(v.intValue()));
        }
        if (types.contains(GoogleHealthDataType.ACTIVE_ZONE_MINUTES)) {
            syncDailyRollup(user, token, GoogleHealthDataType.ACTIVE_ZONE_MINUTES, from, to,
                    (jour, v) -> jour.setMinutesZoneActive(v.intValue()));
        }
        if (types.contains(GoogleHealthDataType.TIME_IN_HEART_RATE_ZONE)) {
            syncZonesCardiaques(user, token, from, to);
        }
        if (types.contains(GoogleHealthDataType.HEART_RATE)) {
            syncHeartRate(user, token, from, to);
        }
        if (types.contains(GoogleHealthDataType.DAILY_RESTING_HEART_RATE)) {
            syncListeSimple(user, token, GoogleHealthDataType.DAILY_RESTING_HEART_RATE, from, to,
                    (jour, v) -> jour.setFcRepos(v.intValue()));
        }
        if (types.contains(GoogleHealthDataType.DAILY_OXYGEN_SATURATION)) {
            syncListeSimple(user, token, GoogleHealthDataType.DAILY_OXYGEN_SATURATION, from, to,
                    SanteJour::setSpo2Pct);
        }
        if (types.contains(GoogleHealthDataType.DAILY_RESPIRATORY_RATE)) {
            syncListeSimple(user, token, GoogleHealthDataType.DAILY_RESPIRATORY_RATE, from, to,
                    SanteJour::setFrequenceRespiratoire);
        }
        if (types.contains(GoogleHealthDataType.DAILY_HEART_RATE_VARIABILITY)) {
            syncHrv(user, token, from, to);
        }
        if (types.contains(GoogleHealthDataType.DAILY_VO2_MAX)) {
            syncVo2Max(user, token, from, to);
        }
        if (types.contains(GoogleHealthDataType.SLEEP)) {
            syncSleep(user, token, from, to);
            completerFcSommeil(user, from, to);
        }

        chargeCardioService.recalculerPlage(user, from, to);
        scoreSommeilService.recalculerPlage(user, from, to);
    }

    private void syncDailyRollup(Utilisateur user, String token, GoogleHealthDataType type, LocalDate from,
            LocalDate to, BiConsumer<SanteJour, Double> appliquer) {
        try {
            int points = 0;
            LocalDate curseur = from;
            while (!curseur.isAfter(to)) {
                LocalDate fin = borneTranche(curseur, to);
                JsonNode reponse = fitbitClient.dailyRollUp(token, type, curseur, fin);
                Map<LocalDate, Double> parDate = GoogleHealthParser.dailyRollupByDate(reponse, type.camelCaseField(),
                        CANDIDATS_ROLLUP);
                points += parDate.size();
                for (Map.Entry<LocalDate, Double> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    appliquer.accept(jour, e.getValue());
                    santeJourRepository.save(jour);
                }
                curseur = fin.plusDays(1);
            }
            marquerSucces(user, type, to, points);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncZonesCardiaques(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.TIME_IN_HEART_RATE_ZONE;
        try {
            int points = 0;
            LocalDate curseur = from;
            while (!curseur.isAfter(to)) {
                LocalDate fin = borneTranche(curseur, to);
                JsonNode reponse = fitbitClient.dailyRollUp(token, type, curseur, fin);
                Map<LocalDate, GoogleHealthParser.ZoneMinutes> parDate = GoogleHealthParser.zoneMinutesByDate(reponse);
                points += parDate.size();
                for (Map.Entry<LocalDate, GoogleHealthParser.ZoneMinutes> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setMinutesZoneBruleuse(e.getValue().bruleuse());
                    jour.setMinutesZoneCardio(e.getValue().cardio());
                    jour.setMinutesZonePic(e.getValue().pic());
                    santeJourRepository.save(jour);
                }
                curseur = fin.plusDays(1);
            }
            marquerSucces(user, type, to, points);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncHeartRate(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.HEART_RATE;
        ZoneId zone = clock.getZone();
        try {
            int points = 0;
            LocalDate jour = from;
            while (!jour.isAfter(to)) {
                Instant debutJour = jour.atStartOfDay(zone).toInstant();
                Instant finJour = jour.plusDays(1).atStartOfDay(zone).toInstant();
                JsonNode reponse = fitbitClient.rollUp(token, type, debutJour, finJour, FENETRE_FC_SECONDES);
                List<GoogleHealthParser.FcBucket> buckets = GoogleHealthParser.heartRateBuckets(reponse);
                points += buckets.size();
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
            marquerSucces(user, type, to, points);
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
            int points = 0;
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, Double> parDate = GoogleHealthParser.dailyDoubleByDate(reponse, type.camelCaseField(),
                        CANDIDATS_LIST);
                points += parDate.size();
                for (Map.Entry<LocalDate, Double> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    appliquer.accept(jour, e.getValue());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to, points);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncHrv(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.DAILY_HEART_RATE_VARIABILITY;
        try {
            int points = 0;
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, GoogleHealthParser.VfcJour> parDate = GoogleHealthParser.hrvByDate(reponse);
                points += parDate.size();
                for (Map.Entry<LocalDate, GoogleHealthParser.VfcJour> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setVfcMs(e.getValue().vfcMs());
                    jour.setVfcSommeilProfondMs(e.getValue().vfcSommeilProfondMs());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to, points);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncVo2Max(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.DAILY_VO2_MAX;
        try {
            int points = 0;
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                Map<LocalDate, GoogleHealthParser.Vo2MaxJour> parDate = GoogleHealthParser.vo2MaxByDate(reponse);
                points += parDate.size();
                for (Map.Entry<LocalDate, GoogleHealthParser.Vo2MaxJour> e : parDate.entrySet()) {
                    SanteJour jour = jourDe(user, e.getKey());
                    jour.setVo2Max(e.getValue().vo2Max());
                    jour.setNiveauAptitude(e.getValue().niveauAptitude());
                    santeJourRepository.save(jour);
                }
                pageToken = GoogleHealthParser.nextPageToken(reponse);
                pages++;
            } while (pageToken != null && pages < MAX_PAGES);
            marquerSucces(user, type, to, points);
        } catch (RuntimeException e) {
            marquerEchec(user, type, e);
        }
    }

    private void syncSleep(Utilisateur user, String token, LocalDate from, LocalDate to) {
        GoogleHealthDataType type = GoogleHealthDataType.SLEEP;
        ZoneId zone = clock.getZone();
        try {
            int points = 0;
            String pageToken = null;
            int pages = 0;
            do {
                JsonNode reponse = fitbitClient.listDataPoints(token, type, from, pageToken);
                List<GoogleHealthParser.SommeilBrut> sessions = GoogleHealthParser.sleepSessions(reponse);
                points += sessions.size();
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
            marquerSucces(user, type, to, points);
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

    // WHY: un appel qui réussit sans rien ramener passait pour un succès. C'est ce qui a
    // masqué pendant des semaines les zones cardiaques, le VO2max et l'agitation du
    // sommeil : le HTTP répondait 200, le parseur ne reconnaissait aucun champ, et l'état
    // restait OK pendant que les écrans affichaient des tirets. Le nombre de points écrits
    // est donc enregistré, et zéro devient un statut à part entière.
    private void marquerSucces(Utilisateur user, GoogleHealthDataType type, LocalDate jusqua, int points) {
        SanteSyncState etat = etatDe(user, type);
        etat.setDerniereDateSynchronisee(jusqua);
        etat.setDerniereExecution(LocalDateTime.now(clock));
        etat.setDernierStatut(points > 0 ? StatutSync.OK : StatutSync.VIDE);
        etat.setDernierMessage(points + " point(s)");
        santeSyncStateRepository.save(etat);
        if (points == 0) {
            log.warn("SANTE_SYNC_VIDE user={} type={} : appel réussi, aucune donnée exploitable",
                    user.getUsername(), type);
        }
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
