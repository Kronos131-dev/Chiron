package com.kronos.chiron.security;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final int MINIMUM_KEY_LENGTH_BYTES = 32;

    private final SecretKey signInKey;

    private final long jwtExpiration;

    public JwtService(@Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration:86400000}") long jwtExpiration) {
        this.signInKey = buildSignInKey(secretKey);
        this.jwtExpiration = jwtExpiration;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(signInKey)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signInKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey buildSignInKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new ChironTechnicalException(
                    "JWT_SECRET est absent ou vide. Définissez une clé hexadécimale d'au moins 64 caractères,"
                            + " par exemple avec `openssl rand -hex 32`.");
        }
        byte[] keyBytes = decodeSecret(secretKey);
        if (keyBytes.length < MINIMUM_KEY_LENGTH_BYTES) {
            throw new ChironTechnicalException("JWT_SECRET est trop court : " + keyBytes.length
                    + " octets décodés, alors que HMAC-SHA256 en exige " + MINIMUM_KEY_LENGTH_BYTES + ".");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static byte[] decodeSecret(String secretKey) {
        try {
            return Decoders.BASE64.decode(secretKey);
        } catch (DecodingException e) {
            throw new ChironTechnicalException("JWT_SECRET n'est pas décodable en base64.", e);
        }
    }
}
