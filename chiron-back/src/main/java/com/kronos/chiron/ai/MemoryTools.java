package com.kronos.chiron.ai;

import com.kronos.chiron.entity.ChironMemoryNote;
import com.kronos.chiron.entity.MemoryNoteType;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.service.MemoryNoteService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Outils donnant à Chiron une mémoire long-terme par utilisateur.
 * Les notes survivent à la fenêtre de chat (20 messages) et au redémarrage.
 */
@Component
@RequiredArgsConstructor
public class MemoryTools {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRANCE);

    private final UtilisateurRepository utilisateurRepository;
    private final MemoryNoteService memoryNoteService;

    @Tool("Enregistre une note durable sur l'utilisateur. Types : BLESSURE (douleur, limitation médicale), PREFERENCE (goûts, régime alimentaire), OBJECTIF (objectif précis/chiffré), ENGAGEMENT (promesse), NOTE_LIBRE (autre).")
    public String enregistrerNote(@ToolMemoryId String userId, String type, String contenu) {
        Utilisateur user = loadUser(userId);
        if (contenu == null || contenu.isBlank()) {
            return "Le contenu de la note est vide — rien enregistré.";
        }
        MemoryNoteType t;
        try {
            t = MemoryNoteType.valueOf(type == null ? "NOTE_LIBRE" : type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return "Type de note invalide. Valeurs autorisées : BLESSURE, PREFERENCE, OBJECTIF, ENGAGEMENT, NOTE_LIBRE.";
        }
        ChironMemoryNote saved = memoryNoteService.save(user, t, contenu.trim());
        return "Note enregistrée (id=" + saved.getId() + ", type=" + t.name() + ").";
    }

    @Tool("Récupère les notes durables de l'utilisateur. Si type fourni (BLESSURE, PREFERENCE, OBJECTIF, ENGAGEMENT, NOTE_LIBRE), filtre dessus ; sinon les 20 plus récentes.")
    public String getMesNotes(@ToolMemoryId String userId, String type) {
        Utilisateur user = loadUser(userId);
        List<ChironMemoryNote> notes;
        if (type != null && !type.isBlank()) {
            MemoryNoteType t;
            try {
                t = MemoryNoteType.valueOf(type.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return "Type de note invalide. Valeurs autorisées : BLESSURE, PREFERENCE, OBJECTIF, ENGAGEMENT, NOTE_LIBRE.";
            }
            notes = memoryNoteService.getByType(user, t);
        } else {
            notes = memoryNoteService.getRecent(user, 20);
        }

        if (notes.isEmpty()) {
            return "Aucune note enregistrée pour le moment.";
        }
        StringBuilder res = new StringBuilder("Notes (").append(notes.size()).append(") :\n");
        for (ChironMemoryNote n : notes) {
            res.append("- [#").append(n.getId()).append(" | ").append(n.getType().name()).append(" | ")
                    .append(n.getCreatedAt().toLocalDate().format(DATE_FMT)).append("] ")
                    .append(n.getContent()).append("\n");
        }
        return res.toString();
    }

    @Tool("Supprime une note durable à partir de son identifiant numérique.")
    public String oublierNote(@ToolMemoryId String userId, Long id) {
        if (id == null) return "Identifiant de note manquant.";
        Utilisateur user = loadUser(userId);
        boolean removed = memoryNoteService.delete(user, id);
        return removed
                ? "Note #" + id + " supprimée."
                : "Aucune note #" + id + " trouvée pour cet utilisateur.";
    }

    private Utilisateur loadUser(String userId) {
        return utilisateurRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
    }
}
