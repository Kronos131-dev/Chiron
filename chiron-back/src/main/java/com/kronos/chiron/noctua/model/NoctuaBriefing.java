package com.kronos.chiron.noctua.model;

import com.kronos.chiron.coach.model.Conversation;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "noctua_briefing", uniqueConstraints = @UniqueConstraint(name = "uk_noctua_briefing_declencheur", columnNames = {
        "utilisateur_id", "cle_declencheur"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoctuaBriefing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private NoctuaBriefingType type;

    @Column(name = "date_reference", nullable = false)
    private LocalDate dateReference;

    @Column(name = "cle_declencheur", nullable = false, length = 64)
    private String cleDeclencheur;

    @Column(name = "lu", nullable = false)
    @Builder.Default
    private boolean lu = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
