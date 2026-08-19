package com.kronos.chiron.sante.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sante_sync_state", uniqueConstraints = @UniqueConstraint(name = "uk_sante_sync_state_user_type", columnNames = {
        "utilisateur_id", "type_donnee"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SanteSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "type_donnee", nullable = false, length = 64)
    private String typeDonnee;

    @Column(name = "derniere_date_synchronisee")
    private LocalDate derniereDateSynchronisee;

    @Column(name = "derniere_execution")
    private LocalDateTime derniereExecution;

    @Enumerated(EnumType.STRING)
    @Column(name = "dernier_statut", nullable = false, length = 32)
    private StatutSync dernierStatut;

    @Column(name = "dernier_message", length = 500)
    private String dernierMessage;

    @Builder.Default
    @Column(name = "backfill_termine", nullable = false)
    private boolean backfillTermine = false;
}
