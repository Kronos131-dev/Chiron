package com.kronos.chiron.fitbit;

import java.time.LocalDateTime;

/**
 * État de la liaison Fitbit d'un utilisateur. Calque {@code NutritionLinkStatus}.
 *
 * @param linked         le compte Fitbit est lié
 * @param needsReconnect lié mais plus aucun token utilisable — l'utilisateur doit relier
 * @param fitbitUserId   identifiant Fitbit de l'utilisateur
 * @param scope          scopes OAuth accordés
 * @param linkedAt       date de la liaison
 */
public record FitbitLinkStatus(
        boolean linked,
        boolean needsReconnect,
        String fitbitUserId,
        String scope,
        LocalDateTime linkedAt
) {
    public static FitbitLinkStatus notLinked() {
        return new FitbitLinkStatus(false, false, null, null, null);
    }
}
