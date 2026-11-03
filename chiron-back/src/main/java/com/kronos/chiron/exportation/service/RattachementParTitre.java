package com.kronos.chiron.exportation.service;

import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.persistence.SeanceTitre;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RattachementParTitre {

    private final SeanceRepository seanceRepository;

    public Map<Long, Long> chercher(Long utilisateurId, Map<Long, String> titresParModele) {
        Map<String, Long> modeleParTitre = modelesParTitre(titresParModele);
        if (modeleParTitre.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> rattachees = new LinkedHashMap<>();
        for (SeanceTitre seance : seanceRepository.findTitresCompletesSansModele(utilisateurId)) {
            Long modeleId = modeleParTitre.get(normaliser(seance.getTitre()));
            if (modeleId != null) {
                rattachees.put(seance.getId(), modeleId);
            }
        }
        return rattachees;
    }

    private Map<String, Long> modelesParTitre(Map<Long, String> titresParModele) {
        Map<String, Long> modeleParTitre = new HashMap<>();
        Set<String> ambigus = new HashSet<>();
        titresParModele.forEach((modeleId, titre) -> {
            String cle = normaliser(titre);
            if (!cle.isEmpty() && modeleParTitre.putIfAbsent(cle, modeleId) != null) {
                ambigus.add(cle);
            }
        });
        ambigus.forEach(modeleParTitre::remove);
        return modeleParTitre;
    }

    static String normaliser(String titre) {
        return titre == null ? "" : titre.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
