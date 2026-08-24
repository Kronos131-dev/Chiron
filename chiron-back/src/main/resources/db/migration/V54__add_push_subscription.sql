-- Abonnements Web Push (RFC 8291/8292) d'un utilisateur, un par navigateur/appareil sur lequel
-- il a activé les notifications dans glaux. endpoint et les deux clés viennent tels quels de
-- PushSubscription.toJSON() côté navigateur ; ils ne changent jamais après la souscription
-- initiale, seule sa suppression (désabonnement, ou purge après un 404/410 du service de push)
-- fait disparaître la ligne.

CREATE TABLE push_subscription (
    id             BIGSERIAL   PRIMARY KEY,
    utilisateur_id BIGINT      NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    endpoint       TEXT        NOT NULL,
    cle_p256dh     VARCHAR(255) NOT NULL,
    cle_auth       VARCHAR(255) NOT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscription_user ON push_subscription (utilisateur_id);
