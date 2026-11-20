package edu.dlsu.enrollment.catalog;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import enrollment.catalog.CatalogServiceGrpc;
import enrollment.catalog.ListCoursesRequest;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogServiceTest {
    private HikariDataSource dataSource;
    private Server server;
    private ManagedChannel channel;
    private KeyPair keys;
    private Path publicKeyPath;

    @BeforeEach
    void start() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        publicKeyPath = Files.createTempFile("catalog-test-public-", ".pem");
        String encodedKey = Base64.getMimeEncoder(64, new byte[]{'\n'})
            .encodeToString(keys.getPublic().getEncoded());
        Files.writeString(
            publicKeyPath,
            "-----BEGIN PUBLIC KEY-----\n" + encodedKey + "\n-----END PUBLIC KEY-----\n",
            StandardCharsets.UTF_8);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:catalog" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE courses (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(16), name VARCHAR(100), section VARCHAR(8), schedule VARCHAR(50), capacity INT, status VARCHAR(10), faculty_name VARCHAR(100), UNIQUE(code,section))");
            statement.execute("CREATE TABLE enrollments (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_username VARCHAR(32), course_id BIGINT, enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(student_username,course_id))");
            statement.execute("INSERT INTO courses(code,name,section,schedule,capacity,status,faculty_name) VALUES ('ONE','One Course','A','MW',2,'OPEN','Prof')");
            statement.execute("INSERT INTO enrollments(student_username,course_id) VALUES ('12012345',1)");
        }

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(io.grpc.ServerInterceptors.intercept(
                new CatalogServiceImpl(dataSource),
                new AuthInterceptor(new JwtVerifier(publicKeyPath))))
            .build()
            .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stop() throws Exception {
        channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        dataSource.close();
        Files.deleteIfExists(publicKeyPath);
    }

    @Test
    void rejectsRequestsWithoutBearerToken() {
        StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
            () -> CatalogServiceGrpc.newBlockingStub(channel)
                .listCourses(ListCoursesRequest.getDefaultInstance()));

        assertEquals(Status.Code.UNAUTHENTICATED, error.getStatus().getCode());
    }

    @Test
    void listsCoursesForValidSignedToken() {
        CatalogServiceGrpc.CatalogServiceBlockingStub client =
            CatalogServiceGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(bearerHeader(token())));

        var response = client.listCourses(ListCoursesRequest.getDefaultInstance());

        assertEquals(1, response.getCoursesCount());
        assertEquals("ONE", response.getCourses(0).getCode());
        assertEquals(1, response.getCourses(0).getEnrolled());
    }

    private Metadata bearerHeader(String token) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            "Bearer " + token);
        return headers;
    }

    private String token() {
        Date now = new Date();
        return Jwts.builder()
            .subject("12012345")
            .issuer(JwtVerifier.ISSUER)
            .claim("role", "STUDENT")
            .claim("name", "Test Student")
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 60_000))
            .signWith(keys.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }
}
