package com.kronos.chiron.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service responsible for creating, validating, and extracting information from JSON Web Tokens (JWT).
 * It acts as the core utility for token-based authentication within the application.
 */
@Service
public class JwtService {

    @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}")
    private String secretKey;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    /**
     * Extracts the username (subject) from a given JWT.
     *
     * @param token The JSON Web Token.
     * @return The username embedded within the token.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a specific claim from a JWT using the provided claims resolver function.
     *
     * @param token          The JSON Web Token.
     * @param claimsResolver A function to extract the desired claim from the token's Claims object.
     * @param <T>            The type of the claim being extracted.
     * @return The extracted claim value.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Generates a new JWT for the specified user details without any extra claims.
     *
     * @param userDetails The details of the user for whom the token is generated.
     * @return A valid JSON Web Token string.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a new JWT containing extra custom claims and the specified user details.
     *
     * @param extraClaims Additional claims to embed within the token.
     * @param userDetails The details of the user for whom the token is generated.
     * @return A valid JSON Web Token string.
     */
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Validates a token against a given user's details.
     * Checks whether the username in the token matches the user details and ensures the token is not expired.
     *
     * @param token       The JSON Web Token to validate.
     * @param userDetails The user details to validate against.
     * @return True if the token is valid for the user; false otherwise.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    /**
     * Checks if a token has expired based on its expiration claim.
     *
     * @param token The JSON Web Token.
     * @return True if the current date is after the token's expiration date; false otherwise.
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Extracts the expiration date from a given JWT.
     *
     * @param token The JSON Web Token.
     * @return The token's expiration date.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Parses the JWT to extract all embedded claims.
     *
     * @param token The JSON Web Token.
     * @return A Claims object containing all information from the token payload.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Retrieves the cryptographic signing key based on the application's secret key string.
     *
     * @return A Key object used for HMAC-SHA algorithms.
     */
    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
