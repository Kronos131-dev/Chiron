package com.kronos.chiron.visbody;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Importe un rapport Visbody (PDF) : parse les métriques, identifie l'utilisateur
 * destinataire, puis enregistre un {@link BodyCompositionRecord} (idempotent).
 */
@Service
@RequiredArgsConstructor
public class VisbodyImportService {

    private static final Logger log = LoggerFactory.getLogger(VisbodyImportService.class);

    private final VisbodyPdfParser parser;
    private final BodyCompositionRecordRepository recordRepo;
    private final UtilisateurRepository utilisateurRepo;

    public enum Outcome { IMPORTED, DUPLICATE, USER_NOT_FOUND, INVALID }

    public record ImportResult(Outcome outcome, String detail) {}

    /**
     * Import depuis un upload manuel : l'utilisateur est déjà connu (forcé).
     */
    @Transactional
    public ImportResult importForUser(byte[] pdf, Utilisateur user) {
        VisbodyReport report;
        try {
            report = parser.parse(pdf);
        } catch (RuntimeException e) {
            return new ImportResult(Outcome.INVALID, "PDF illisible : " + e.getMessage());
        }
        if (report.getMesureLe() == null) {
            return new ImportResult(Outcome.INVALID, "Date de détection introuvable dans le PDF.");
        }
        return persist(report, user);
    }

    /**
     * Import depuis un email reçu : résout l'utilisateur via l'expéditeur (le plus
     * fiable), sinon l'email masqué du PDF, sinon le nom du champ « ID ».
     *
     * @param senderEmail adresse « From » du mail (peut être null).
     */
    @Transactional
    public ImportResult importFromEmail(byte[] pdf, String senderEmail) {
        VisbodyReport report;
        try {
            report = parser.parse(pdf);
        } catch (RuntimeException e) {
            return new ImportResult(Outcome.INVALID, "PDF illisible : " + e.getMessage());
        }
        if (report.getMesureLe() == null) {
            return new ImportResult(Outcome.INVALID, "Date de détection introuvable dans le PDF.");
        }

        Optional<Utilisateur> user = resolveUser(report, senderEmail);
        if (user.isEmpty()) {
            log.warn("Visbody : aucun utilisateur trouvé (from={}, id={}, email={})",
                    senderEmail, report.getIdLabel(), report.getMaskedEmail());
            return new ImportResult(Outcome.USER_NOT_FOUND,
                    "Utilisateur introuvable pour ce rapport.");
        }
        return persist(report, user.get());
    }

    // ----------------------------------------------------------------- Matching

    /** Stratégie de match, par ordre de fiabilité décroissante. */
    Optional<Utilisateur> resolveUser(VisbodyReport report, String senderEmail) {
        // 1) Adresse expéditeur exacte.
        if (senderEmail != null && !senderEmail.isBlank()) {
            Optional<Utilisateur> bySender = utilisateurRepo.findByEmail(senderEmail.trim().toLowerCase());
            if (bySender.isPresent()) return bySender;
        }
        // 2) Email masqué du PDF (préfixe…suffixe@domaine).
        Optional<Utilisateur> byMasked = matchMaskedEmail(report.getMaskedEmail());
        if (byMasked.isPresent()) return byMasked;

        // 3) Nom du champ « ID » comparé à nom / prénom.
        if (report.getIdLabel() != null && !report.getIdLabel().isBlank()) {
            String id = report.getIdLabel().trim();
            List<Utilisateur> byName = utilisateurRepo.findByNomIgnoreCaseOrPrenomIgnoreCase(id, id);
            if (byName.size() == 1) return Optional.of(byName.get(0));
            if (byName.size() > 1) {
                log.warn("Visbody : nom « {} » ambigu ({} utilisateurs)", id, byName.size());
            }
        }
        return Optional.empty();
    }

    /**
     * Compare un email masqué « octa****n1er@gmail... » aux emails connus :
     * même domaine (préfixe avant le point), préfixe et suffixe de la partie locale.
     */
    private Optional<Utilisateur> matchMaskedEmail(String masked) {
        if (masked == null || !masked.contains("@") || !masked.contains("*")) return Optional.empty();
        String local = masked.substring(0, masked.indexOf('@'));
        String domainPart = masked.substring(masked.indexOf('@') + 1).toLowerCase(); // "gmail..."
        Matcher m = Pattern.compile("^([^*]+)\\*+([^*]+)$").matcher(local);
        if (!m.matches()) return Optional.empty();
        String prefix = m.group(1).toLowerCase();
        String suffix = m.group(2).toLowerCase();
        String domainStem = domainPart.replaceAll("[^a-z0-9.].*$", "").replaceAll("\\.+$", "");

        List<Utilisateur> candidates = utilisateurRepo.findByEmailIsNotNull().stream()
                .filter(u -> {
                    String e = u.getEmail().toLowerCase();
                    int at = e.indexOf('@');
                    if (at < 0) return false;
                    String eLocal = e.substring(0, at);
                    String eDomain = e.substring(at + 1);
                    return eLocal.startsWith(prefix) && eLocal.endsWith(suffix)
                            && eDomain.startsWith(domainStem);
                })
                .toList();
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    // ----------------------------------------------------------------- Persist

    private ImportResult persist(VisbodyReport report, Utilisateur user) {
        if (recordRepo.existsByUtilisateurAndMesureLe(user, report.getMesureLe())) {
            return new ImportResult(Outcome.DUPLICATE, "Scan déjà importé (" + report.getMesureLe() + ").");
        }
        BodyCompositionRecord rec = BodyCompositionRecord.builder()
                .utilisateur(user)
                .mesureLe(report.getMesureLe())
                .note(report.getNote())
                .poids(report.getPoids())
                .masseMusculaire(report.getMasseMusculaire())
                .mms(report.getMms())
                .mgc(report.getMgc())
                .mmc(report.getMmc())
                .tgcPct(report.getTgcPct())
                .imc(report.getImc())
                .rth(report.getRth())
                .mbKcal(report.getMbKcal())
                .ageMetabolique(report.getAgeMetabolique())
                .graisseViscerale(report.getGraisseViscerale())
                .eauTotale(report.getEauTotale())
                .eauIntra(report.getEauIntra())
                .eauExtra(report.getEauExtra())
                .ratioEcwTbw(report.getRatioEcwTbw())
                .masseProteine(report.getMasseProteine())
                .selInorganique(report.getSelInorganique())
                .mgcBrasGauche(report.getMgcBrasGauche())
                .mgcBrasDroit(report.getMgcBrasDroit())
                .mgcTronc(report.getMgcTronc())
                .mgcJambeGauche(report.getMgcJambeGauche())
                .mgcJambeDroite(report.getMgcJambeDroite())
                .muscleBrasGauche(report.getMuscleBrasGauche())
                .muscleBrasDroit(report.getMuscleBrasDroit())
                .muscleTronc(report.getMuscleTronc())
                .muscleJambeGauche(report.getMuscleJambeGauche())
                .muscleJambeDroite(report.getMuscleJambeDroite())
                .source("VISBODY_PDF")
                .build();
        recordRepo.save(rec);

        // Met à jour le poids de corps courant si présent (utile aux autres stats).
        if (report.getPoids() != null) {
            user.setPoidsCorps(report.getPoids());
            utilisateurRepo.save(user);
        }
        log.info("Visbody : scan importé pour {} ({})", user.getUsername(), report.getMesureLe());
        return new ImportResult(Outcome.IMPORTED, "Scan importé (" + report.getMesureLe() + ").");
    }
}
