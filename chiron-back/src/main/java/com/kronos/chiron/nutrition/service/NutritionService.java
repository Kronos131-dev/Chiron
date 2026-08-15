package com.kronos.chiron.nutrition.service;

import com.kronos.chiron.nutrition.dto.NutritionLinkStatus;

public interface NutritionService {

    class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) {
            super(msg);
        }
    }

    class NotLinkedException extends RuntimeException {
    }

    class ExpiredException extends RuntimeException {
    }

    NutritionLinkStatus link(String chironUsername, String olympusPseudo, String olympusPassword);

    NutritionLinkStatus getStatus(String chironUsername);

    void unlink(String chironUsername);

    String getValidToken(String chironUsername);

    void invalidateLink(String chironUsername);
}
