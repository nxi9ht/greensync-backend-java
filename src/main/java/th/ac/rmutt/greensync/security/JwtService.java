package th.ac.rmutt.greensync.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the app's own JWTs. Claim shape (sub/email/orgId/role) mirrors the
 * NestJS backend's tokens so the rest of the API surface (and the Angular frontend) never has to
 * know which backend issued the token. The configured secret is SHA-256 hashed into the actual
 * HMAC key so any secret length works, since HS256 requires a 256-bit key.
 */
@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long expirationMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
    this.signingKey = Keys.hmacShaKeyFor(sha256(secret));
    this.expirationMinutes = expirationMinutes;
  }

  public String issueToken(Map<String, Object> claims) {
    Instant now = Instant.now();
    return Jwts.builder()
        .claims(claims)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(signingKey)
        .compact();
  }

  /** @return the token's claims, or {@code null} if the token is missing, expired, or invalid. */
  public Claims parseClaims(String token) {
    try {
      return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    } catch (JwtException | IllegalArgumentException e) {
      return null;
    }
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
