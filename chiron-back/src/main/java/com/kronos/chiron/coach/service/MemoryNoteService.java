package com.kronos.chiron.coach.service;

import com.kronos.chiron.coach.model.ChironMemoryNote;
import com.kronos.chiron.coach.model.MemoryNoteType;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.util.List;

public interface MemoryNoteService {

    ChironMemoryNote save(Utilisateur user, MemoryNoteType type, String content);

    List<ChironMemoryNote> getRecent(Utilisateur user, int limit);

    List<ChironMemoryNote> getByType(Utilisateur user, MemoryNoteType type);

    boolean delete(Utilisateur user, Long id);
}
