package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.dto.SanteActiviteDetailDto;
import com.kronos.chiron.sante.dto.SanteActiviteDto;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteFcJourDto;
import com.kronos.chiron.sante.dto.SanteFcPointDto;
import com.kronos.chiron.sante.dto.SanteJourDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.dto.SanteSommeilDto;
import com.kronos.chiron.sante.dto.SanteSyncEtatDto;
import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.PreparationService;
import com.kronos.chiron.sante.service.SanteQueryService;
import com.kronos.chiron.sante.service.SeuilsCardiaquesService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanteQueryServiceImpl implements SanteQueryService {

    private static final double CIBLE_BASSE_RATIO = 0.8;
    private static final double CIBLE_HAUTE_RATIO = 1.3;
    private static final int SEMAINES_CIBLE = 4;
    private static final int MAX_JOURS = 366;

    private final FitbitService fitbitService;
    private final SanteJourRepository santeJourRepository;
    private final SanteSommeilRepository santeSommeilRepository;
    private final SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    private final SanteSyncStateRepository santeSyncStateRepository;
    private final SanteActiviteRepository santeActiviteRepository;
    private final PreparationService preparationService;
    private final SeuilsCardiaquesService seuilsCardiaquesService;

    private final Clock clock;

    @Override
    public SanteResumeDto getResume(Utilisateur utilisateur) {
        FitbitLinkStatus statut = fitbitService.getStatus(utilisateur.getUsername());
        if (!statut.linked()) return SanteResumeDto.notLinked();
        if (statut.needsReconnect()) return SanteResumeDto.reconnectNeeded();

        LocalDate today = LocalDate.now(clock);
        SanteJour jour = santeJourRepository.findByUtilisateurAndDate(utilisateur, today).orElse(null);
        Integer scoreSommeil = santeSommeilRepository
                .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(utilisateur, today)
                .map(SanteSommeil::getScore)
                .orElse(null);
        Double chargeHebdo = sommeChargeCardio(utilisateur, today.minusDays(6), today);
        Integer preparation = preparationService.calculer(utilisateur, today);

        if (jour == null) {
            return new SanteResumeDto(true, false, today, null, null, null, null, null, null, scoreSommeil,
                    chargeHebdo, preparation);
        }
        return new SanteResumeDto(true, false, today, jour.getPas(), jour.getDistanceM(), jour.getCaloriesTotales(),
                jour.getCaloriesActives(), jour.getMinutesZoneActive(), jour.getFcRepos(), scoreSommeil, chargeHebdo,
                preparation);
    }

    @Override
    public List<SanteJourDto> getJours(Utilisateur utilisateur, int jours) {
        LocalDate[] plage = plageDepuisAujourdhui(jours);
        return santeJourRepository
                .findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, plage[0], plage[1]).stream()
                .map(this::versDto)
                .toList();
    }

    @Override
    public List<SanteFcPointDto> getFrequenceCardiaqueJour(Utilisateur utilisateur, LocalDate date) {
        LocalDateTime debut = date.atStartOfDay();
        LocalDateTime fin = date.plusDays(1).atStartOfDay();
        return santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(utilisateur, debut, fin)
                .stream()
                .map(fc -> new SanteFcPointDto(fc.getHorodatage(), fc.getFcMin(), fc.getFcMoyenne(), fc.getFcMax()))
                .toList();
    }

    @Override
    public List<SanteFcJourDto> getFrequenceCardiaquePlage(Utilisateur utilisateur, int jours) {
        LocalDate[] plage = plageDepuisAujourdhui(jours);
        return santeJourRepository
                .findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, plage[0], plage[1]).stream()
                .map(j -> new SanteFcJourDto(j.getDate(), j.getFcMin(), j.getFcMoyenne(), j.getFcMax(),
                        j.getFcRepos()))
                .toList();
    }

    @Override
    public List<SanteSommeilDto> getSommeil(Utilisateur utilisateur, int jours) {
        LocalDate[] plage = plageDepuisAujourdhui(jours);
        return santeSommeilRepository
                .findByUtilisateurAndDateBetweenOrderByDebutAsc(utilisateur, plage[0], plage[1]).stream()
                .map(this::versDto)
                .toList();
    }

    @Override
    public List<SanteCardioHebdoDto> getCardioHebdo(Utilisateur utilisateur, int semaines) {
        int n = Math.max(1, Math.min(semaines, 52));
        LocalDate today = LocalDate.now(clock);
        List<SanteCardioHebdoDto> result = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate finSemaine = today.minusDays(7L * i);
            LocalDate debutSemaine = finSemaine.minusDays(6);
            Double charge = sommeChargeCardio(utilisateur, debutSemaine, finSemaine);
            Integer minutesActives = sommeMinutesZoneActive(utilisateur, debutSemaine, finSemaine);
            Double moyenne = moyenneChargeSemainesPrecedentes(utilisateur, debutSemaine, SEMAINES_CIBLE);
            Double cibleBasse = moyenne != null ? moyenne * CIBLE_BASSE_RATIO : null;
            Double cibleHaute = moyenne != null ? moyenne * CIBLE_HAUTE_RATIO : null;
            result.add(new SanteCardioHebdoDto(debutSemaine, charge, cibleBasse, cibleHaute, minutesActives));
        }
        return result;
    }

    private LocalDate[] plageDepuisAujourdhui(int jours) {
        int n = Math.max(1, Math.min(jours, MAX_JOURS));
        LocalDate to = LocalDate.now(clock);
        return new LocalDate[]{to.minusDays(n - 1L), to};
    }

    @Override
    public List<SanteSyncEtatDto> getSyncEtat(Utilisateur utilisateur) {
        return santeSyncStateRepository.findByUtilisateur(utilisateur).stream()
                .map(e -> new SanteSyncEtatDto(e.getTypeDonnee(), e.getDerniereDateSynchronisee(),
                        e.getDerniereExecution(), e.getDernierStatut().name(), e.getDernierMessage()))
                .toList();
    }

    @Override
    public List<SanteActiviteDto> getActivites(Utilisateur utilisateur, int jours, SourceActivite sourceFiltre) {
        LocalDate[] plage = plageDepuisAujourdhui(jours);
        LocalDateTime debut = plage[0].atStartOfDay();
        LocalDateTime fin = plage[1].plusDays(1).atStartOfDay();
        return santeActiviteRepository
                .findByUtilisateurAndStartTimeBetweenOrderByStartTimeDesc(utilisateur, debut, fin).stream()
                .filter(a -> sourceFiltre == null || a.getSource() == sourceFiltre)
                .map(this::versDto)
                .toList();
    }

    @Override
    public Optional<SanteActiviteDetailDto> getActiviteDetail(Utilisateur utilisateur, Long id) {
        SanteActivite activite = santeActiviteRepository.findById(id).orElse(null);
        if (activite == null || !activite.getUtilisateur().getId().equals(utilisateur.getId())) {
            return Optional.empty();
        }
        List<SanteFcPointDto> points = santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(utilisateur, activite.getStartTime(),
                        activite.getEndTime())
                .stream()
                .map(fc -> new SanteFcPointDto(fc.getHorodatage(), fc.getFcMin(), fc.getFcMoyenne(), fc.getFcMax()))
                .toList();
        SeuilsCardiaquesDto seuils = seuilsCardiaquesService.calculer(utilisateur);
        return Optional.of(new SanteActiviteDetailDto(versDto(activite), points, seuils));
    }

    private SanteActiviteDto versDto(SanteActivite a) {
        return new SanteActiviteDto(a.getId(), a.getSource(), a.getTypeActivite(), a.getStartTime(),
                a.getEndTime(), a.getCalories(), a.isCaloriesEstimees(), a.getFcMoyenne(), a.getFcMin(),
                a.getFcMax(),
                a.getMinutesZoneBasse(), a.getMinutesZoneBruleuse(), a.getMinutesZoneCardio(),
                a.getMinutesZonePic(), a.getMinutesZoneActive(), a.getChargeCardio(),
                a.getSeance() != null ? a.getSeance().getId() : null,
                a.getStatutEnrichissement() == StatutEnrichissement.EN_ATTENTE);
    }

    private SanteJourDto versDto(SanteJour j) {
        return new SanteJourDto(j.getDate(), j.getPas(), j.getDistanceM(), j.getCaloriesTotales(),
                j.getCaloriesActives(), j.getMinutesZoneActive(), j.getMinutesZoneBruleuse(),
                j.getMinutesZoneCardio(), j.getMinutesZonePic(), j.getFcRepos(), j.getFcMin(), j.getFcMoyenne(),
                j.getFcMax(), j.getVfcMs(), j.getChargeCardio(),
                j.getFrequenceRespiratoire());
    }

    private SanteSommeilDto versDto(SanteSommeil s) {
        return new SanteSommeilDto(s.getDate(), s.getDebut(), s.getFin(), s.isSieste(), s.isStadesDisponibles(),
                s.getMinutesEndormi(), s.getMinutesEveille(), s.getMinutesAvantEndormissement(),
                s.getMinutesApresReveil(), s.getMinutesProfond(), s.getMinutesLeger(), s.getMinutesParadoxal(),
                s.getMinutesAgite(), s.getNbReveils(), s.getFcSommeilMoyenne(), s.getScore(), s.getScoreDuree(),
                s.getScoreComposition(), s.getScoreRestauration());
    }

    private Double sommeChargeCardio(Utilisateur utilisateur, LocalDate from, LocalDate to) {
        List<Double> charges = santeJourRepository
                .findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, from, to).stream()
                .map(SanteJour::getChargeCardio)
                .filter(Objects::nonNull)
                .toList();
        return charges.isEmpty() ? null : charges.stream().mapToDouble(Double::doubleValue).sum();
    }

    private Integer sommeMinutesZoneActive(Utilisateur utilisateur, LocalDate from, LocalDate to) {
        List<Integer> valeurs = santeJourRepository
                .findByUtilisateurAndDateBetweenOrderByDateAsc(utilisateur, from, to).stream()
                .map(SanteJour::getMinutesZoneActive)
                .filter(Objects::nonNull)
                .toList();
        return valeurs.isEmpty() ? null : valeurs.stream().mapToInt(Integer::intValue).sum();
    }

    private Double moyenneChargeSemainesPrecedentes(Utilisateur utilisateur, LocalDate debutSemaineCourante,
            int nbSemaines) {
        List<Double> charges = new ArrayList<>();
        for (int i = 1; i <= nbSemaines; i++) {
            LocalDate debut = debutSemaineCourante.minusDays(7L * i);
            LocalDate fin = debut.plusDays(6);
            Double charge = sommeChargeCardio(utilisateur, debut, fin);
            if (charge != null) charges.add(charge);
        }
        if (charges.size() < nbSemaines) return null;
        return charges.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }
}
