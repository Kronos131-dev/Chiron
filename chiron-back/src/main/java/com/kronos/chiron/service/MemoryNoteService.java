package com.kronos.chiron.service;

import com.kronos.chiron.entity.ChironMemoryNote;
import com.kronos.chiron.entity.MemoryNoteType;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.ChironMemoryNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemoryNoteService {

    private final ChironMemoryNoteRepository repository;

    @Transactional
    public ChironMemoryNote save(Utilisateur user, MemoryNoteType type, String content) {
        ChironMemoryNote note = ChironMemoryNote.builder()
                .utilisateur(user)
                .type(type)
                .content(content)
                .build();
        return repository.save(note);
    }

    @Transactional(readOnly = true)
    public List<ChironMemoryNote> getRecent(Utilisateur user, int limit) {
        return repository.findByUtilisateurOrderByCreatedAtDesc(user, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ChironMemoryNote> getByType(Utilisateur user, MemoryNoteType type) {
        return repository.findByUtilisateurAndTypeOrderByCreatedAtDesc(user, type);
    }

    /**
     * Suppression d'une note ; renvoie true si supprimée, false si introuvable ou pas la propriété de l'user.
     */
    @Transactional
    public boolean delete(Utilisateur user, Long id) {
        Optional<ChironMemoryNote> note = repository.findByIdAndUtilisateur(id, user);
        if (note.isEmpty()) return false;
        repository.delete(note.get());
        return true;
    }
}
