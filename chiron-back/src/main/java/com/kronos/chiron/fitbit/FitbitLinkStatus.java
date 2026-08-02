package com.kronos.chiron.fitbit;

import java.time.LocalDateTime;

public record FitbitLinkStatus(
        boolean linked,
        boolean needsReconnect,
        String fitbitUserId,
        String scope,
        LocalDateTime linkedAt) {
    public static FitbitLinkStatus notLinked() {
        return new FitbitLinkStatus(false, false, null, null, null);
    }
}
