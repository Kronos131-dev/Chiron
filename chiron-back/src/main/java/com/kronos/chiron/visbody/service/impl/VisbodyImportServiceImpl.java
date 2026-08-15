package com.kronos.chiron.visbody.service.impl;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.visbody.dto.VisbodyReport;
import com.kronos.chiron.visbody.model.BodyCompositionRecord;
import com.kronos.chiron.visbody.persistence.BodyCompositionRecordRepository;
import com.kronos.chiron.visbody.service.VisbodyImportService;
import com.kronos.chiron.visbody.service.VisbodyPdfParser;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VisbodyImportServiceImpl implements VisbodyImportService {

    private static final Logger log = LoggerFactory.getLogger(VisbodyImportServiceImpl.class);

    private final VisbodyPdfParser parser;
    private final BodyCompositionRecordRepository recordRepo;
    private final UtilisateurRepository utilisateurRepo;

    @Transactional
    @Override
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
        return persist(report, user, "VISBODY_PDF");
    }

    @Transactional
    @Override
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
        return persist(report, user.get(), "VISBODY_PDF");
    }

    Optional<Utilisateur> resolveUser(VisbodyReport report, String senderEmail) {
        Optional<Utilisateur> byName = matchByName(report.getIdLabel());
        if (byName.isPresent()) return byName;

        if (senderEmail != null && !senderEmail.isBlank()) {
            Optional<Utilisateur> bySender = utilisateurRepo.findByEmail(senderEmail.trim().toLowerCase());
            if (bySender.isPresent()) return bySender;
        }
        return Optional.empty();
    }

    private Optional<Utilisateur> matchByName(String idLabel) {
        if (idLabel == null || idLabel.isBlank()) return Optional.empty();
        String id = idLabel.trim();

        List<Utilisateur> matches = utilisateurRepo.findByNomIgnoreCaseOrPrenomIgnoreCase(id, id);

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

    @Transactional
    @Override
    public ImportResult persist(VisbodyReport report, Utilisateur user, String source) {
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
                .source(source)
                .build();
        recordRepo.save(rec);

        if (report.getPoids() != null) {
            user.setPoidsCorps(report.getPoids());
            utilisateurRepo.save(user);
        }
        log.info("{} : scan importé pour {} ({})", source, user.getUsername(), report.getMesureLe());
        return new ImportResult(Outcome.IMPORTED, "Scan importé (" + report.getMesureLe() + ").");
    }
}
