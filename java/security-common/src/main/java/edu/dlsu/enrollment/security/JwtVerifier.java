package edu.dlsu.enrollment.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class JwtVerifier {
    public static final String ISSUER = "enrollment-auth";

    private final PublicKey publicKey;

    public JwtVerifier() {
        this(resolveDefaultPublicKeyPath());
    }

    public JwtVerifier(String publicKeyPath) {
        this(readPublicKey(Path.of(publicKeyPath)));
    }

    public JwtVerifier(Path publicKeyPath) {
        this(readPublicKey(publicKeyPath));
    }

    public JwtVerifier(PublicKey publicKey) {
        if (publicKey == null) {
            throw new IllegalArgumentException("Public key is required");
        }
        this.publicKey = publicKey;
    }

    public AuthUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Missing token");
        }

        Claims claims;
        try {
            claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JwtException("Invalid JWT", e);
        }

        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        String name = claims.get("name", String.class);

        if (username == null || username.isBlank()
            || role == null || role.isBlank()
            || !Role.isValid(role)) {
            throw new JwtException("Missing or invalid JWT identity claims");
        }

        return new AuthUser(username, role, name == null ? "" : name);
    }

    public Claims parseClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtException("Missing token");
        }
        return Jwts.parser()
            .verifyWith(publicKey)
            .requireIssuer(ISSUER)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private static String resolveDefaultPublicKeyPath() {
        String configured = System.getenv("JWT_PUBLIC_KEY_PATH");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path direct = workingDirectory.resolve("keys/public.pem");
        if (Files.isRegularFile(direct)) {
            return direct.toString();
        }
        return workingDirectory.resolve("../keys/public.pem").normalize().toString();
    }

    private static PublicKey readPublicKey(Path path) {
        try {
            String pem = Files.readString(path, StandardCharsets.UTF_8);
            String base64 = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Cannot load RSA public key from " + path, e);
        }
    }
}
