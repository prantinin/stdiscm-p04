package edu.dlsu.enrollment.gradesupload;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import enrollment.gradesupload.GradeUploadServiceGrpc;
import enrollment.gradesupload.UploadGradeRequest;
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
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GradesUploadServiceTest {
    private HikariDataSource dataSource;
    private Server server;
    private ManagedChannel channel;
    private KeyPair keys;

    @BeforeEach
    void startServer() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:grades_upload" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        dataSource = new HikariDataSource(config);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE grades (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_username VARCHAR(32) NOT NULL, course_code VARCHAR(16) NOT NULL, term VARCHAR(16) NOT NULL, grade VARCHAR(4) NOT NULL, uploaded_by VARCHAR(32) NOT NULL, uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(student_username, course_code, term))");
        }
        keys = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
            .addService(io.grpc.ServerInterceptors.intercept(new GradesUploadServiceImpl(dataSource), new AuthInterceptor(new JwtVerifier(keys.getPublic()))))
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
    void facultyCanUploadAndReuploadUpdatesTheExistingGrade() throws Exception {
        var client = clientFor("fac001", "FACULTY");
        client.uploadGrade(grade("2026-T1", "3.5"));
        client.uploadGrade(grade("2026-T1", "4.0"));
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*), MAX(grade), MAX(uploaded_by) FROM grades WHERE student_username='12012345' AND course_code='CCPROG3' AND term='2026-T1'")) {
            result.next();
            assertEquals(1, result.getInt(1));
            assertEquals("4.0", result.getString(2));
            assertEquals("fac001", result.getString(3));
        }
    }

    @Test
    void acceptsEveryGradeInTheSupportedScale() {
        var client = clientFor("fac001", "FACULTY");
        for (String value : new String[]{"4.0", "3.5", "3.0", "2.5", "2.0", "1.5", "1.0", "0.0"}) {
            assertEquals(value, client.uploadGrade(grade("2026-T1", value)).getGrade());
        }
    }

    @Test
    void rejectsStudentRoleAndTermFour() {
        var studentError = assertThrows(StatusRuntimeException.class,
            () -> clientFor("12012345", "STUDENT").uploadGrade(grade("2026-T1", "3.5")));
        assertEquals(Status.Code.PERMISSION_DENIED, studentError.getStatus().getCode());
        var termError = assertThrows(StatusRuntimeException.class,
            () -> clientFor("fac001", "FACULTY").uploadGrade(grade("2026-T4", "3.5")));
        assertEquals(Status.Code.INVALID_ARGUMENT, termError.getStatus().getCode());
    }

    @Test
    void validatesRequiredFieldsLengthsAndAllowedGrades() {
        var client = clientFor("fac001", "FACULTY");
        assertInvalid(client, grade("2026-T1", "1.7"));
        assertInvalid(client, grade("2026-T1", "3.5").toBuilder().setStudentUsername("s".repeat(33)).build());
        assertInvalid(client, grade("2026-T1", "3.5").toBuilder().setCourseCode("c".repeat(17)).build());
        assertInvalid(client, grade("2026-T1", "3.5").toBuilder().setStudentUsername("").build());
        assertInvalid(client, grade("2026-T1", "3.5").toBuilder().setCourseCode("").build());
        assertInvalid(client, grade("", "3.5"));
        assertInvalid(client, grade("2026-T1", ""));
    }

    private void assertInvalid(GradeUploadServiceGrpc.GradeUploadServiceBlockingStub client, UploadGradeRequest request) {
        var error = assertThrows(StatusRuntimeException.class, () -> client.uploadGrade(request));
        assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
    }

    private UploadGradeRequest grade(String term, String value) {
        return UploadGradeRequest.newBuilder().setStudentUsername("12012345").setCourseCode("CCPROG3").setTerm(term).setGrade(value).build();
    }

    private GradeUploadServiceGrpc.GradeUploadServiceBlockingStub clientFor(String subject, String role) {
        Date now = new Date();
        String token = Jwts.builder().issuer(JwtVerifier.ISSUER).subject(subject).claim("role", role)
            .issuedAt(now).expiration(new Date(now.getTime() + 60_000))
            .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + token);
        return GradeUploadServiceGrpc.newBlockingStub(ClientInterceptors.intercept(
            channel, MetadataUtils.newAttachHeadersInterceptor(headers)));
    }
}
