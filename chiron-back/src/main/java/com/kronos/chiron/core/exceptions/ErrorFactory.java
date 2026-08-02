package com.kronos.chiron.core.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public final class ErrorFactory {

    private ErrorFactory() {
    }

    private static ErrorResponseException buildError(HttpStatus status, String detail, Throwable cause) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        return new ErrorResponseException(status, problemDetail, cause);
    }

    public static ErrorResponseException badRequest(String detail) {
        return badRequest(detail, null);
    }

    public static ErrorResponseException badRequest(String detail, Throwable cause) {
        return buildError(HttpStatus.BAD_REQUEST, detail, cause);
    }

    public static ErrorResponseException unauthorized(String detail) {
        return buildError(HttpStatus.UNAUTHORIZED, detail, null);
    }

    public static ErrorResponseException forbidden(String detail) {
        return buildError(HttpStatus.FORBIDDEN, detail, null);
    }

    public static ErrorResponseException forbidden() {
        return forbidden("Accès refusé à cette ressource");
    }

    public static ErrorResponseException notFound(String detail) {
        return buildError(HttpStatus.NOT_FOUND, detail, null);
    }

    public static ErrorResponseException notFound(String resourceName, Object id) {
        return notFound("Aucun " + resourceName + " trouvé pour l'identifiant '" + id + "'");
    }

    public static ErrorResponseException conflict(String detail) {
        return buildError(HttpStatus.CONFLICT, detail, null);
    }

    public static ErrorResponseException unprocessableEntity(String detail) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, detail, null);
    }

    public static ErrorResponseException serviceUnavailable(String detail, Throwable cause) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, detail, cause);
    }
}
