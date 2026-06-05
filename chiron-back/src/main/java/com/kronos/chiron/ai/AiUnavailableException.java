package com.kronos.chiron.ai;

/**
 * Levée quand le coach IA reste injoignable après réessais et repli sur le fournisseur de secours.
 * Traduite en HTTP 503 par le GlobalExceptionHandler (et non un 500 brut).
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
