package com.kronos.chiron.repository;

import com.kronos.chiron.entity.ChironMemoryNote;
import com.kronos.chiron.entity.MemoryNoteType;
import com.kronos.chiron.entity.Utilisateur;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChironMemoryNoteRepository extends JpaRepository<ChironMemoryNote, Long> {

    List<ChironMemoryNote> findByUtilisateurOrderByCreatedAtDesc(Utilisateur utilisateur, Pageable pageable);

    List<ChironMemoryNote> findByUtilisateurAndTypeOrderByCreatedAtDesc(Utilisateur utilisateur, MemoryNoteType type);

    Optional<ChironMemoryNote> findByIdAndUtilisateur(Long id, Utilisateur utilisateur);
}
