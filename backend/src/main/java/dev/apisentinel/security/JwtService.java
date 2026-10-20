package dev.apisentinel.security;

import dev.apisentinel.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey signingKey;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        byte[] bytes = properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must be at least 32 UTF-8 bytes");
        this.signingKey = new SecretKeySpec(bytes, "HmacSHA256");
        this.ttlSeconds = Math.max(60, properties.security().tokenTtlMinutes() * 60);
    }

    public String issue(UUID userId, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .claim("ver", tokenVersion)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(ttlSeconds)))
            .signWith(signingKey)
            .compact();
    }

    public TokenClaims parse(String token) throws JwtException {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        return new TokenClaims(UUID.fromString(claims.getSubject()), claims.get("ver", Integer.class));
    }

    public long ttlSeconds() { return ttlSeconds; }
    public record TokenClaims(UUID subject, Integer tokenVersion) { }
}
