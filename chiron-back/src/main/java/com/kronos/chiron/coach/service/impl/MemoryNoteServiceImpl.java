package com.kronos.chiron.coach.service.impl;

import com.kronos.chiron.coach.service.MemoryNoteService;

import com.kronos.chiron.coach.model.ChironMemoryNote;
import com.kronos.chiron.coach.model.MemoryNoteType;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.coach.persistence.ChironMemoryNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemoryNoteServiceImpl implements MemoryNoteService {

    private final ChironMemoryNoteRepository repository;

    @Transactional
    @Override
    public ChironMemoryNote save(Utilisateur user, MemoryNoteType type, String content) {
        ChironMemoryNote note = ChironMemoryNote.builder()
                .utilisateur(user)
                .type(type)
                .content(content)
                .build();
        return repository.save(note);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChironMemoryNote> getRecent(Utilisateur user, int limit) {
        return repository.findByUtilisateurOrderByCreatedAtDesc(user, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ChironMemoryNote> getByType(Utilisateur user, MemoryNoteType type) {
        return repository.findByUtilisateurAndTypeOrderByCreatedAtDesc(user, type);
    }

    @Transactional
    @Override
    public boolean delete(Utilisateur user, Long id) {
        Optional<ChironMemoryNote> note = repository.findByIdAndUtilisateur(id, user);
        if (note.isEmpty()) return false;
        repository.delete(note.get());
        return true;
    }
}
