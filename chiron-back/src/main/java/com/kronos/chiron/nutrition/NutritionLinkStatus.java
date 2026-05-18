package com.kronos.chiron.nutrition;

import java.time.LocalDateTime;

public record NutritionLinkStatus(
        boolean linked,
        boolean expired,
        String olympusUsername,
        LocalDateTime linkedAt,
        LocalDateTime expiresAt
) {
    public static NutritionLinkStatus notLinked() {
        return new NutritionLinkStatus(false, false, null, null, null);
    }
}
