package com.kronos.chiron.exportation.service.impl;

import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.exportation.dto.ExportProgrammeDto;
import com.kronos.chiron.exportation.dto.ExportProgrammeRequest;
import com.kronos.chiron.exportation.dto.FichierExporte;
import com.kronos.chiron.exportation.dto.ModeleExport;
import com.kronos.chiron.exportation.dto.SeanceExport;
import com.kronos.chiron.exportation.model.ExportProgramme;
import com.kronos.chiron.exportation.model.ExportProgrammeModele;
import com.kronos.chiron.exportation.model.ExportProgrammeSeance;
import com.kronos.chiron.exportation.persistence.ExportProgrammeRepository;
import com.kronos.chiron.exportation.service.ChargeurDonneesExport;
import com.kronos.chiron.exportation.service.ExportProgrammeService;
import com.kronos.chiron.exportation.service.RattachementParTitre;
import com.kronos.chiron.exportation.service.classeur.ClasseurXlsx;
import com.kronos.chiron.exportation.service.classeur.DonneesExport;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

@Service
@RequiredArgsConstructor
public class ExportProgrammeServiceImpl implements ExportProgrammeService {

    static final int MODELES_MAX = 20;
    static final int SEANCES_MAX = 2000;

    private final ExportProgrammeRepository exportProgrammeRepository;
    private final SeanceRepository seanceRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ChargeurDonneesExport chargeurDonnees;
    private final RattachementParTitre rattachementParTitre;
    private final ClasseurXlsx classeurXlsx;
    private final Clock clock;

    @Transactional(readOnly = true)
    @Override
    public List<ExportProgrammeDto> lister(String username) {
        Utilisateur user = utilisateur(username);
        return exportProgrammeRepository.findByUtilisateurIdOrderByCreeADesc(user.getId()).stream()
                .map(this::enDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public ExportProgrammeDto detail(String username, Long id) {
        return enDto(exportDe(username, id));
    }

    @Transactional
    @Override
    public ExportProgrammeDto creer(String username, ExportProgrammeRequest requete) {
        return enDto(creerLigne(username, requete));
    }

    @Transactional
    @Override
    public ExportProgrammeDto modifier(String username, Long id, ExportProgrammeRequest requete) {
        return enDto(modifierLigne(username, id, requete));
    }

    @Transactional
    @Override
    public void supprimer(String username, Long id) {
        exportProgrammeRepository.delete(exportDe(username, id));
    }

    @Transactional(readOnly = true)
    @Override
    public FichierExporte telecharger(String username, Long id) {
        ExportProgramme export = exportDe(username, id);
        DonneesExport donnees = chargeurDonnees.charger(export);
        return new FichierExporte(nomDeFichier(export.getNom()), classeurXlsx.generer(donnees));
    }

    private ExportProgramme creerLigne(String username, ExportProgrammeRequest requete) {
        Utilisateur user = utilisateur(username);
        Selection selection = valider(user, requete);
        LocalDateTime maintenant = LocalDateTime.now(clock);
        ExportProgramme export = ExportProgramme.builder()
                .utilisateur(user)
                .nom(selection.nom())
                .creeA(maintenant)
                .modifieA(maintenant)
                .build();
        int ordre = 0;
        for (Long modeleId : selection.modeleIds()) {
            export.ajouterModele(ExportProgrammeModele.builder().modeleId(modeleId).ordre(ordre++).build());
        }
        selection.seances().forEach(s -> export.ajouterSeance(
                ExportProgrammeSeance.builder().seanceId(s.seanceId()).modeleId(s.modeleId()).build()));
        return exportProgrammeRepository.save(export);
    }

    private ExportProgramme modifierLigne(String username, Long id, ExportProgrammeRequest requete) {
        ExportProgramme export = exportDe(username, id);
        Selection selection = valider(utilisateur(username), requete);

        export.setNom(selection.nom());
        Map<Long, ExportProgrammeModele> existants = export.getModeles().stream()
                .collect(Collectors.toMap(ExportProgrammeModele::getModeleId, m -> m, (a, b) -> a));
        export.getModeles().removeIf(m -> !selection.modeleIds().contains(m.getModeleId()));
        int ordre = 0;
        for (Long modeleId : selection.modeleIds()) {
            ExportProgrammeModele ligne = existants.get(modeleId);
            if (ligne == null) {
                export.ajouterModele(ExportProgrammeModele.builder().modeleId(modeleId).ordre(ordre).build());
            } else {
                ligne.setOrdre(ordre);
            }
            ordre++;
        }

        Map<Long, ExportProgrammeSeance> ajoutees = export.getSeances().stream()
                .collect(Collectors.toMap(ExportProgrammeSeance::getSeanceId, s -> s, (a, b) -> a));
        Set<Long> voulues = selection.seances().stream().map(SeanceExport::seanceId).collect(Collectors.toSet());
        export.getSeances().removeIf(s -> !voulues.contains(s.getSeanceId()));
        for (SeanceExport voulue : selection.seances()) {
            ExportProgrammeSeance ligne = ajoutees.get(voulue.seanceId());
            if (ligne == null) {
                export.ajouterSeance(ExportProgrammeSeance.builder().seanceId(voulue.seanceId())
                        .modeleId(voulue.modeleId()).build());
            } else {
                ligne.setModeleId(voulue.modeleId());
            }
        }

        export.setModifieA(LocalDateTime.now(clock));
        return exportProgrammeRepository.save(export);
    }

    private record Selection(String nom, List<Long> modeleIds, List<SeanceExport> seances) {
    }

    private Selection valider(Utilisateur user, ExportProgrammeRequest requete) {
        String nom = requete.nom() == null ? "" : requete.nom().trim();
        if (nom.isEmpty() || nom.length() > 120) {
            throw badRequest("Le nom de l'export doit faire entre 1 et 120 caractères.");
        }
        List<Long> modeleIds = requete.modeleIds() == null
                ? List.of()
                : requete.modeleIds().stream().filter(Objects::nonNull).distinct().toList();
        if (modeleIds.isEmpty()) {
            throw badRequest("Choisis au moins une séance de ton programme.");
        }
        if (modeleIds.size() > MODELES_MAX) {
            throw badRequest("Un export ne peut pas contenir plus de " + MODELES_MAX + " séances de programme.");
        }
        Map<Long, Seance> modeles = new HashMap<>();
        seanceRepository.findAllById(modeleIds).forEach(m -> modeles.put(m.getId(), m));
        for (Long modeleId : modeleIds) {
            Seance modele = modeles.get(modeleId);
            boolean valable = modele != null && !modele.isHistorique() && modele.getUtilisateur() != null
                    && Objects.equals(modele.getUtilisateur().getId(), user.getId());
            if (!valable) {
                throw badRequest("La séance du programme " + modeleId + " n'existe pas ou n'est pas à toi.");
            }
        }

        List<SeanceExport> seances = requete.seances() == null
                ? List.of()
                : requete.seances().stream().filter(Objects::nonNull).toList();
        if (seances.size() > SEANCES_MAX) {
            throw badRequest("Trop de séances ajoutées à la main (" + SEANCES_MAX + " au plus).");
        }
        Set<Long> vues = new HashSet<>();
        for (SeanceExport seance : seances) {
            if (seance.seanceId() == null || !vues.add(seance.seanceId())) {
                throw badRequest("Une séance est ajoutée deux fois.");
            }
            if (seance.modeleId() == null || !modeleIds.contains(seance.modeleId())) {
                throw badRequest("La séance " + seance.seanceId() + " doit être rattachée à une séance du programme"
                        + " choisie.");
            }
        }
        if (!vues.isEmpty()) {
            int valides = seanceRepository.findCompletesDeLUtilisateur(user.getId(), vues).size();
            if (valides != vues.size()) {
                throw badRequest("Une séance ajoutée n'existe pas, n'est pas à toi ou n'est pas terminée.");
            }
        }
        return new Selection(nom, modeleIds, seances);
    }

    private Utilisateur utilisateur(String username) {
        return utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + username));
    }

    private ExportProgramme exportDe(String username, Long id) {
        Utilisateur user = utilisateur(username);
        return exportProgrammeRepository.findByIdAndUtilisateurId(id, user.getId())
                .orElseThrow(() -> notFound("export", id));
    }

    private ExportProgrammeDto enDto(ExportProgramme export) {
        List<Long> modeleIds = export.getModeles().stream().map(ExportProgrammeModele::getModeleId).toList();
        Map<Long, String> titres = new HashMap<>();
        seanceRepository.findAllById(modeleIds).forEach(m -> titres.put(m.getId(), m.getTitre()));
        List<ModeleExport> modeles = new ArrayList<>();
        for (Long modeleId : modeleIds) {
            modeles.add(new ModeleExport(modeleId, titres.get(modeleId)));
        }
        List<SeanceExport> ajoutees = export.getSeances().stream()
                .map(s -> new SeanceExport(s.getSeanceId(), s.getModeleId()))
                .toList();
        Set<Long> toutes = new HashSet<>(seanceRepository.findIdsCompletesParModele(modeleIds));
        Map<Long, String> titresParModele = new LinkedHashMap<>();
        modeleIds.forEach(modeleId -> titresParModele.put(modeleId, titres.get(modeleId)));
        toutes.addAll(rattachementParTitre.chercher(export.getUtilisateur().getId(), titresParModele).keySet());
        ajoutees.forEach(s -> toutes.add(s.seanceId()));
        return new ExportProgrammeDto(export.getId(), export.getNom(), export.getCreeA(), modeles, ajoutees,
                toutes.size());
    }

    private String nomDeFichier(String nom) {
        String sansAccent = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String propre = sansAccent.replaceAll("[^A-Za-z0-9 _-]", "").trim().replaceAll("\\s+", "-");
        return (propre.isEmpty() ? "export-chiron" : propre) + ".xlsx";
    }
}
