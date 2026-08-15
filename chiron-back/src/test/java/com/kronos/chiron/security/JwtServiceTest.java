package com.kronos.chiron.security;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;
import com.kronos.chiron.utilisateur.model.Role;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.jsonwebtoken.ExpiredJwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86400000L);
    }

    private Utilisateur buildUser(String username) {
        return Utilisateur.builder()
                .id(1L)
                .username(username)
                .password("encoded")
                .role(Role.USER)
                .build();
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = jwtService.generateToken(buildUser("alice"));
        assertThat(token).isNotBlank();
    }

    @Test
    void extractUsername_returnsCorrectSubject() {
        Utilisateur user = buildUser("bob");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.extractUsername(token)).isEqualTo("bob");
    }

    @Test
    void generateToken_withExtraClaims_embedsClaims() {
        Utilisateur user = buildUser("charlie");
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        String token = jwtService.generateToken(claims, user);
        assertThat(jwtService.extractUsername(token)).isEqualTo("charlie");
    }

    @Test
    void isTokenValid_validTokenAndMatchingUser_returnsTrue() {
        Utilisateur user = buildUser("dave");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_validTokenButDifferentUser_returnsFalse() {
        Utilisateur user = buildUser("eve");
        String token = jwtService.generateToken(user);
        Utilisateur otherUser = buildUser("mallory");
        assertThat(jwtService.isTokenValid(token, otherUser)).isFalse();
    }

    @Test
    void isTokenValid_expiredToken_throwsExpiredJwtException() {
        JwtService shortLivedService = new JwtService(SECRET, -1000L);

        Utilisateur user = buildUser("frank");
        String token = shortLivedService.generateToken(user);
        // JJWT throws ExpiredJwtException when parsing an expired token
        assertThatThrownBy(() -> shortLivedService.isTokenValid(token, user))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void extractClaim_extractsExpirationDate() {
        Utilisateur user = buildUser("grace");
        String token = jwtService.generateToken(user);
        Date expiration = jwtService.extractClaim(token, claims -> claims.getExpiration());
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    void generateToken_differentUsers_produceDifferentTokens() {
        String t1 = jwtService.generateToken(buildUser("user1"));
        String t2 = jwtService.generateToken(buildUser("user2"));
        assertThat(t1).isNotEqualTo(t2);
    }

    @Test
    void constructor_nullSecret_refusesToStart() {
        assertThatThrownBy(() -> new JwtService(null, 86400000L))
                .isInstanceOf(ChironTechnicalException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void constructor_blankSecret_refusesToStart() {
        assertThatThrownBy(() -> new JwtService("   ", 86400000L))
                .isInstanceOf(ChironTechnicalException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void constructor_secretShorterThanHmacMinimum_refusesToStart() {
        assertThatThrownBy(() -> new JwtService("404E635266556A586E327235", 86400000L))
                .isInstanceOf(ChironTechnicalException.class)
                .hasMessageContaining("trop court");
    }

    @Test
    void constructor_secretNotBase64_refusesToStart() {
        assertThatThrownBy(() -> new JwtService("clé secrète avec des espaces et des accents !", 86400000L))
                .isInstanceOf(ChironTechnicalException.class)
                .hasMessageContaining("base64");
    }
}
