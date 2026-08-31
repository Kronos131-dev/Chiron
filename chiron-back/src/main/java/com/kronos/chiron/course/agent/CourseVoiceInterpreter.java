package com.kronos.chiron.course.agent;

import com.kronos.chiron.course.dto.CommandeVoixDto;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface CourseVoiceInterpreter {
    @SystemMessage("""
            Tu interprètes des commandes vocales pendant une course à pied.
            La user dit quelque chose comme 'allure 10 kilomètres heure' ou 'ralentis' ou 'pause'.
            Tu dois répondre avec du JSON au format : {"nom": "<commande>", "cibleMinParKm": <nombre ou null>}.
            Commandes possibles (valeurs pour 'nom') : allure, distance, duree, bilan, pause, reprendre, plusVite, moinsVite, cible, inconnu.
            Pour 'cibleMinParKm' : si la user dit une VITESSE en km/h (ex: 12 km/h), convertis en allure min/km avec la formule minParKm = 60 / vitesseKmH.
            Si c'est déjà une ALLURE en minutes/km (ex: 5:30 ou 5 minutes 30), utilise-la directement.
            Si c'est une autre commande sans cible, mets cibleMinParKm à null.
            Exemple : 'ralentis' → {"nom": "moinsVite", "cibleMinParKm": null}.
            Exemple : 'allure 12 kilomètres heure' → {"nom": "allure", "cibleMinParKm": 5.0}.
            Réponds UNIQUEMENT en JSON, rien d'autre.
            """)
    CommandeVoixDto interpreter(@UserMessage String transcript);
}
