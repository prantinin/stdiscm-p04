package edu.dlsu.enrollment.auth;

import enrollment.auth.AuthServiceGrpc;
import enrollment.auth.LoginRequest;
import enrollment.auth.LoginResponse;
import enrollment.auth.LogoutRequest;
import enrollment.auth.LogoutResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AuthServiceImpl extends AuthServiceGrpc.AuthServiceImplBase {
    private static final Logger LOGGER = Logger.getLogger(AuthServiceImpl.class.getName());
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String DUMMY_PASSWORD_HASH =
        "$2a$10$y6uXssrAAwZD63rPiYNtV.otGwAq4pM9cPwmBOvCHa1NCPkB4/jiS";
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(60);
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final int MAX_TOKEN_LENGTH = 8192;

    private final DataSource dataSource;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Clock clock;

    public AuthServiceImpl(DataSource dataSource) {
        this(dataSource, loadPrivateKey(), loadPublicKey(), Clock.systemUTC());
    }

    AuthServiceImpl(DataSource dataSource, PrivateKey privateKey, PublicKey publicKey) {
        this(dataSource, privateKey, publicKey, Clock.systemUTC());
    }

    AuthServiceImpl(DataSource dataSource, PrivateKey privateKey, PublicKey publicKey, Clock clock) {
        if (dataSource == null || privateKey == null || publicKey == null || clock == null) {
            throw new IllegalArgumentException("Data source, RSA keys, and clock are required");
        }
        this.dataSource = dataSource;
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.clock = clock;
    }

    @Override
    public void login(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        String username = request.getUsername().trim();
        String password = request.getPassword();
        if (username.isEmpty() || username.length() > MAX_USERNAME_LENGTH
            || password.isEmpty()
            || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                .withDescription("Username or password has an invalid length")
                .asRuntimeException());
            return;
        }

        try {
            User user = findUser(username);
            String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.passwordHash();
            boolean passwordMatches;
            try {
                passwordMatches = BCrypt.checkpw(password, passwordHash);
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.SEVERE, "Invalid BCrypt hash stored for user " + username, e);
                responseObserver.onError(Status.INTERNAL
                    .withDescription("Authentication service error")
                    .asRuntimeException());
                return;
            }

            if (user == null || !passwordMatches) {
                responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription(INVALID_CREDENTIALS)
                    .asRuntimeException());
                return;
            }

            Instant issuedAt = clock.instant();
            Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
            String token = Jwts.builder()
                .issuer("enrollment-auth")
                .subject(user.username())
                .claim("role", user.role())
                .claim("name", user.fullName())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

            responseObserver.onNext(LoginResponse.newBuilder()
                .setToken(token)
                .setUsername(user.username())
                .setFullName(user.fullName())
                .setRole(user.role())
                .setExpiresAt(expiresAt.getEpochSecond())
                .build());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during login", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Authentication service error")
                .asRuntimeException());
        } catch (JwtException e) {
            LOGGER.log(Level.SEVERE, "Could not issue authentication token", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Authentication service error")
                .asRuntimeException());
        }
    }

    @Override
    public void logout(LogoutRequest request, StreamObserver<LogoutResponse> responseObserver) {
        String token = request.getToken();
        if (token.isBlank() || token.length() > MAX_TOKEN_LENGTH) {
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription("Invalid token")
                .asRuntimeException());
            return;
        }

        final Claims claims;
        try {
            Jws<Claims> parsed = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer("enrollment-auth")
                .build()
                .parseSignedClaims(token);
            if (!"RS256".equals(parsed.getHeader().getAlgorithm())) {
                throw new JwtException("Unexpected JWT signing algorithm");
            }
            claims = parsed.getPayload();
            if (claims.getId() == null || claims.getId().isBlank()
                || claims.getId().length() > 64 || claims.getExpiration() == null) {
                throw new JwtException("JWT is missing revocation claims");
            }
        } catch (JwtException | IllegalArgumentException e) {
            responseObserver.onError(Status.UNAUTHENTICATED
                .withDescription("Invalid token")
                .asRuntimeException());
            return;
        }

        String sql = """
            INSERT INTO revoked_tokens (jti, expires_at) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)
            """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, claims.getId());
            statement.setObject(2, LocalDateTime.ofInstant(
                claims.getExpiration().toInstant(), ZoneOffset.UTC));
            statement.executeUpdate();
            responseObserver.onNext(LogoutResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during logout", e);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Authentication service error")
                .asRuntimeException());
        }
    }

    private User findUser(String username) throws SQLException {
        String sql = "SELECT username, password_hash, full_name, role FROM users WHERE username = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                return new User(
                    results.getString("username"),
                    results.getString("password_hash"),
                    results.getString("full_name"),
                    results.getString("role"));
            }
        }
    }

    private static PrivateKey loadPrivateKey() {
        Path path = configuredKeyPath("JWT_PRIVATE_KEY_PATH", "private.pem");
        try {
            byte[] encoded = decodePem(path, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load RSA private key from " + path, e);
        }
    }

    private static PublicKey loadPublicKey() {
        Path path = configuredKeyPath("JWT_PUBLIC_KEY_PATH", "public.pem");
        try {
            byte[] encoded = decodePem(path, "PUBLIC KEY");
            return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load RSA public key from " + path, e);
        }
    }

    private static Path configuredKeyPath(String environmentVariable, String fileName) {
        String configured = System.getenv(environmentVariable);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }

        Path workingDirectory = Path.of(System.getProperty("user.dir"));
        Path[] candidates = {
            workingDirectory.resolve("keys").resolve(fileName),
            workingDirectory.resolve("../keys").resolve(fileName).normalize(),
            workingDirectory.resolve("../../keys").resolve(fileName).normalize()
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return candidates[0];
    }

    private static byte[] decodePem(Path path, String keyType) throws IOException {
        String pem = Files.readString(path, StandardCharsets.UTF_8);
        String base64 = pem
            .replace("-----BEGIN " + keyType + "-----", "")
            .replace("-----END " + keyType + "-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private record User(String username, String passwordHash, String fullName, String role) {
    }
}
