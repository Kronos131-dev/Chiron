package com.kronos.chiron.core.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorFactoryTest {

    @Test
    void badRequest_carriesFourHundredAndTheDetail() {
        ErrorResponseException ex = ErrorFactory.badRequest("poids négatif");

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getBody().getDetail()).isEqualTo("poids négatif");
    }

    @Test
    void badRequest_keepsTheUnderlyingCause() {
        Throwable cause = new IllegalStateException("racine");

        assertThat(ErrorFactory.badRequest("invalide", cause)).hasCause(cause);
    }

    @Test
    void unauthorized_carriesFourHundredAndOne() {
        assertThat(ErrorFactory.unauthorized("non authentifié").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forbidden_carriesFourHundredAndThree() {
        assertThat(ErrorFactory.forbidden("interdit").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void forbidden_withoutDetail_stillExplainsItself() {
        assertThat(ErrorFactory.forbidden().getBody().getDetail()).isNotBlank();
    }

    @Test
    void notFound_carriesFourHundredAndFour() {
        assertThat(ErrorFactory.notFound("séance absente").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void notFound_withResourceAndId_namesBothInTheDetail() {
        String detail = ErrorFactory.notFound("seance", 42L).getBody().getDetail();

        assertThat(detail).contains("seance").contains("42");
    }

    @Test
    void conflict_carriesFourHundredAndNine() {
        assertThat(ErrorFactory.conflict("déjà pris").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void unprocessableEntity_carriesFourHundredAndTwentyTwo() {
        assertThat(ErrorFactory.unprocessableEntity("incohérent").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void serviceUnavailable_carriesFiveHundredAndThree() {
        assertThat(ErrorFactory.serviceUnavailable("coach indisponible", null).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
