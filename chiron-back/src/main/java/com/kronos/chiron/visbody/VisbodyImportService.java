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

    /**
     * Stratégie de match. Visbody envoie toujours les rapports depuis sa propre
     * adresse ({@code no_reply@email.visbody.com}) et l'email du PDF est tronqué
     * (peu fiable). Le critère <b>principal et fiable est donc le champ « ID »</b>
     * du rapport (nom / prénom de l'utilisateur). L'expéditeur ne sert que de repli
     * pour un éventuel envoi manuel depuis l'email du compte.
     */
    Optional<Utilisateur> resolveUser(VisbodyReport report, String senderEmail) {
        // 1) Champ « ID » du rapport comparé au nom / prénom du compte.
        Optional<Utilisateur> byName = matchByName(report.getIdLabel());
        if (byName.isPresent()) return byName;

        // 2) Repli : envoi manuel depuis l'adresse exacte d'un compte.
        if (senderEmail != null && !senderEmail.isBlank()) {
            Optional<Utilisateur> bySender = utilisateurRepo.findByEmail(senderEmail.trim().toLowerCase());
            if (bySender.isPresent()) return bySender;
        }
        return Optional.empty();
    }

    /**
     * Résout l'utilisateur à partir du champ « ID » du rapport. Gère un ID à un seul
     * mot (« Tellier » → nom ou prénom) comme un ID « Prénom Nom ». N'enregistre pas
     * si l'ID est ambigu (plusieurs comptes correspondent).
     */
    private Optional<Utilisateur> matchByName(String idLabel) {
        if (idLabel == null || idLabel.isBlank()) return Optional.empty();
        String id = idLabel.trim();

        // ID complet == nom OU prénom.
        List<Utilisateur> matches = utilisateurRepo.findByNomIgnoreCaseOrPrenomIgnoreCase(id, id);

        // ID « Prénom Nom » : essaie la combinaison, puis le dernier mot seul.
        if (matches.isEmpty() && id.contains(" ")) {
            String[] parts = id.split("\\s+");
            String first = parts[0];
            String last = parts[parts.length - 1];
            matches = utilisateurRepo.findByPrenomIgnoreCaseAndNomIgnoreCase(first, last);
            if (matches.isEmpty()) {
                matches = utilisateurRepo.findByNomIgnoreCaseOrPrenomIgnoreCase(last, last);
            }
        }

        if (matches.size() == 1) return Optional.of(matches.get(0));
        if (matches.size() > 1) {
            log.warn("Visbody : ID « {} » ambigu ({} comptes correspondent) — scan non enregistré", id, matches.size());
        } else {
            log.warn("Visbody : aucun compte avec nom/prénom « {} »", id);
        }
        return Optional.empty();
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
