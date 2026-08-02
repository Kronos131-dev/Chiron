package com.kronos.chiron.core.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public final class ChironTechnicalException extends RuntimeException {

    public ChironTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChironTechnicalException(String message) {
        super(message);
    }
}
