package edu.dlsu.enrollment.gradesview;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import enrollment.gradesview.GetMyGradesRequest;
import enrollment.gradesview.GradesViewServiceGrpc;
import io.grpc.ClientInterceptors;
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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GradesViewServiceTest {
    private HikariDataSource dataSource;
    private Server server;
    private ManagedChannel channel;
    private KeyPair keys;

    @BeforeEach
    void startServer() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:grades_view" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE grades (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_username VARCHAR(32), course_code VARCHAR(16), term VARCHAR(16), grade VARCHAR(4), uploaded_by VARCHAR(32), uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("INSERT INTO grades (student_username, course_code, term, grade, uploaded_by) VALUES ('12012345','CCPROG3','2025-T1','3.5','fac001'), ('12012345','AAA','2025-T1','4.0','fac001'), ('12012345','ZZZ','2024-T2','2.5','fac001'), ('12012346','CSARCH2','2025-T1','4.0','fac001')");
        }
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(io.grpc.ServerInterceptors.intercept(new GradesViewServiceImpl(dataSource), new AuthInterceptor(new JwtVerifier(keys.getPublic()))))
            .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (channel != null) channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        if (server != null) server.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
        if (dataSource != null) dataSource.close();
    }

    @Test
    void returnsOnlyVerifiedStudentGradesInTermAndCourseOrder() {
        var response = clientFor("12012345", "STUDENT").getMyGrades(GetMyGradesRequest.getDefaultInstance());
        assertEquals(3, response.getGradesCount());
        assertEquals("2024-T2", response.getGrades(0).getTerm());
        assertEquals("AAA", response.getGrades(1).getCourseCode());
        assertEquals("CCPROG3", response.getGrades(2).getCourseCode());
        response.getGradesList().forEach(grade -> assertEquals("12012345", grade.getStudentUsername()));
    }

    @Test
    void rejectsFacultyAndUnsignedTokens() {
        var facultyError = assertThrows(StatusRuntimeException.class,
            () -> clientFor("fac001", "FACULTY").getMyGrades(GetMyGradesRequest.getDefaultInstance()));
        assertEquals(Status.Code.PERMISSION_DENIED, facultyError.getStatus().getCode());

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer not.a.valid.signature");
        var unsignedClient = GradesViewServiceGrpc.newBlockingStub(ClientInterceptors.intercept(
            channel, MetadataUtils.newAttachHeadersInterceptor(headers)));
        var unsignedError = assertThrows(StatusRuntimeException.class,
            () -> unsignedClient.getMyGrades(GetMyGradesRequest.getDefaultInstance()));
        assertEquals(Status.Code.UNAUTHENTICATED, unsignedError.getStatus().getCode());
    }

    private GradesViewServiceGrpc.GradesViewServiceBlockingStub clientFor(String subject, String role) {
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token(subject, role));
        return GradesViewServiceGrpc.newBlockingStub(ClientInterceptors.intercept(
            channel, MetadataUtils.newAttachHeadersInterceptor(headers)));
    }

    private String token(String subject, String role) {
        Date now = new Date();
        return Jwts.builder().issuer(JwtVerifier.ISSUER).subject(subject).claim("role", role)
            .issuedAt(now).expiration(new Date(now.getTime() + 60_000))
            .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
