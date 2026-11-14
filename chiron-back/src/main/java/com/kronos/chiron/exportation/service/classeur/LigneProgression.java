package com.kronos.chiron.exportation.service.classeur;

import java.util.List;

record LigneProgression(String cle, String nom, String unite, List<Double> valeurs, List<String> series,
        List<String> commentaires) {
}
