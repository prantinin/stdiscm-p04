package edu.dlsu.enrollment.auth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import enrollment.auth.AuthServiceGrpc;
import enrollment.auth.LoginRequest;
import enrollment.auth.LoginResponse;
import enrollment.auth.LogoutRequest;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {
    private HikariDataSource dataSource;
    private Server server;
    private ManagedChannel channel;
    private AuthServiceGrpc.AuthServiceBlockingStub client;
    private KeyPair keys;

    @BeforeEach
    void startServer() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:auth" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        dataSource = new HikariDataSource(config);

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users ("
                + "username VARCHAR(32) PRIMARY KEY, "
                + "password_hash CHAR(60) NOT NULL, "
                + "full_name VARCHAR(100) NOT NULL, "
                + "role VARCHAR(16) NOT NULL)");
            statement.execute("CREATE TABLE revoked_tokens ("
                + "jti VARCHAR(64) PRIMARY KEY, expires_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO users VALUES ('12012345', '"
                + BCrypt.hashpw("password123", BCrypt.gensalt(10))
                + "', 'Juan Dela Cruz', 'STUDENT')");
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();

        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(new AuthServiceImpl(dataSource, keys.getPrivate(), keys.getPublic()))
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(serverName)
            .directExecutor()
            .build();
        client = AuthServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void loginReturnsRs256TokenWithExpectedIdentityAndLifetime() {
        Instant before = Instant.now();
        LoginResponse response = client.login(LoginRequest.newBuilder()
            .setUsername("12012345")
            .setPassword("password123")
            .build());
        Instant after = Instant.now();

        Jws<Claims> signed = Jwts.parser()
            .verifyWith(keys.getPublic())
            .requireIssuer("enrollment-auth")
            .build()
            .parseSignedClaims(response.getToken());
        Claims claims = signed.getPayload();

        assertEquals("RS256", signed.getHeader().getAlgorithm());
        assertEquals("12012345", response.getUsername());
        assertEquals("Juan Dela Cruz", response.getFullName());
        assertEquals("STUDENT", response.getRole());
        assertEquals("12012345", claims.getSubject());
        assertEquals("STUDENT", claims.get("role", String.class));
        assertEquals("Juan Dela Cruz", claims.get("name", String.class));
        assertNotNull(claims.getId());
        assertEquals(claims.getExpiration().toInstant().getEpochSecond(), response.getExpiresAt());
        assertTrue(!claims.getIssuedAt().toInstant().isBefore(before.minusSeconds(1)));
        assertTrue(!claims.getIssuedAt().toInstant().isAfter(after.plusSeconds(1)));
        assertEquals(60 * 60, Duration.between(
            claims.getIssuedAt().toInstant(),
            claims.getExpiration().toInstant()).toSeconds());
    }

    @Test
    void unknownUsernameAndWrongPasswordHaveTheSameUnauthenticatedError() {
        StatusRuntimeException unknownUser = assertThrows(StatusRuntimeException.class,
            () -> client.login(LoginRequest.newBuilder()
                .setUsername("missing")
                .setPassword("password123")
                .build()));
        StatusRuntimeException wrongPassword = assertThrows(StatusRuntimeException.class,
            () -> client.login(LoginRequest.newBuilder()
                .setUsername("12012345")
                .setPassword("wrong-password")
                .build()));

        assertEquals(Status.Code.UNAUTHENTICATED, unknownUser.getStatus().getCode());
        assertEquals(unknownUser.getStatus().getCode(), wrongPassword.getStatus().getCode());
        assertEquals(unknownUser.getStatus().getDescription(),
            wrongPassword.getStatus().getDescription());
    }

    @Test
    void loginRejectsInputsOutsideSupportedLengths() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
            () -> client.login(LoginRequest.newBuilder()
                .setUsername("x".repeat(33))
                .setPassword("password123")
                .build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
    }

    @Test
    void logoutStoresJtiAndIsIdempotent() throws Exception {
        String token = client.login(LoginRequest.newBuilder()
            .setUsername("12012345")
            .setPassword("password123")
            .build())
            .getToken();
        String jti = Jwts.parser()
            .verifyWith(keys.getPublic())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getId();

        client.logout(LogoutRequest.newBuilder().setToken(token).build());
        client.logout(LogoutRequest.newBuilder().setToken(token).build());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                 "SELECT COUNT(*) FROM revoked_tokens WHERE jti = '" + jti + "'")) {
            results.next();
            assertEquals(1, results.getInt(1));
        }
    }

    @Test
    void logoutRejectsInvalidToken() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
            () -> client.logout(LogoutRequest.newBuilder()
                .setToken("not-a-jwt")
                .build()));

        assertEquals(Status.Code.UNAUTHENTICATED, error.getStatus().getCode());
    }
}
