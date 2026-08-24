package com.kronos.chiron.push.persistence;

import com.kronos.chiron.push.model.PushSubscription;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    List<PushSubscription> findByUtilisateur(Utilisateur utilisateur);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    void deleteByUtilisateurAndEndpoint(Utilisateur utilisateur, String endpoint);
}
