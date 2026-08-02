package com.kronos.chiron.coach.persistence;

import com.kronos.chiron.coach.model.ChironMemoryNote;
import com.kronos.chiron.coach.model.MemoryNoteType;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChironMemoryNoteRepository extends JpaRepository<ChironMemoryNote, Long> {

    List<ChironMemoryNote> findByUtilisateurOrderByCreatedAtDesc(Utilisateur utilisateur, Pageable pageable);

    List<ChironMemoryNote> findByUtilisateurAndTypeOrderByCreatedAtDesc(Utilisateur utilisateur, MemoryNoteType type);

    Optional<ChironMemoryNote> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
