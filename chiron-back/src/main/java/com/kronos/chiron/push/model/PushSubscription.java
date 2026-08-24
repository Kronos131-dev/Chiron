package com.kronos.chiron.push.model;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscription", uniqueConstraints = @UniqueConstraint(name = "uk_push_subscription_endpoint", columnNames = {
        "endpoint"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "utilisateur_id", nullable = false)
    private Utilisateur utilisateur;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "cle_p256dh", nullable = false)
    private String cleP256dh;

    @Column(name = "cle_auth", nullable = false)
    private String cleAuth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
