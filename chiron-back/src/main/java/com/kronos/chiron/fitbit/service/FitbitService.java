package com.kronos.chiron.fitbit.service;

import com.kronos.chiron.fitbit.dto.FitbitDashboardDto;
import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;

public interface FitbitService {

    class NotLinkedException extends RuntimeException {
    }

    class ExpiredException extends RuntimeException {
    }

    class InvalidStateException extends RuntimeException {
    }

    String buildAuthorizationUrl(String chironUsername);

    FitbitLinkStatus handleCallback(String code, String state);

    FitbitLinkStatus getStatus(String chironUsername);

    void unlink(String chironUsername);

    String getValidToken(String chironUsername);

    FitbitDashboardDto getDashboard(String chironUsername, int days);
}
