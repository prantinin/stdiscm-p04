package edu.dlsu.enrollment.gradesupload;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import enrollment.gradesupload.GradeUploadServiceGrpc;
import enrollment.gradesupload.DeleteGradeRequest;
import enrollment.gradesupload.EditGradeRequest;
import enrollment.gradesupload.ListUploadedGradesRequest;
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
import java.sql.PreparedStatement;
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

    @Test
    void facultyListsOnlyGradesTheyUploaded() throws Exception {
        seedGrade("12012345", "CSARCH2", "2026-T2", "4.0", "fac001");
        seedGrade("12012345", "CCPROG3", "2026-T1", "3.5", "fac001");
        seedGrade("12012345", "AAA", "2026-T1", "3.0", "fac001");
        seedGrade("12012346", "CSARCH2", "2026-T1", "4.0", "fac002");

        var response = clientFor("fac001", "FACULTY").listUploadedGrades(ListUploadedGradesRequest.getDefaultInstance());

        assertEquals(3, response.getGradesCount());
        assertEquals("2026-T1", response.getGrades(0).getTerm());
        assertEquals("AAA", response.getGrades(0).getCourseCode());
        assertEquals("CCPROG3", response.getGrades(1).getCourseCode());
        assertEquals("2026-T2", response.getGrades(2).getTerm());
        assertEquals("12012345", response.getGrades(0).getStudentUsername());
        assertEquals("fac001", response.getGrades(0).getUploadedBy());
    }

    @Test
    void facultyCanEditOnlyTheirExistingGradeWithoutCreatingDuplicates() throws Exception {
        long gradeId = seedGrade("12012345", "CCPROG3", "2026-T1", "3.5", "fac001");

        var response = clientFor("fac001", "FACULTY").editGrade(EditGradeRequest.newBuilder()
            .setGradeId(gradeId).setGrade("4.0").build());

        assertEquals(gradeId, response.getGrade().getGradeId());
        assertEquals("4.0", response.getGrade().getGrade());
        assertEquals("12012345", response.getGrade().getStudentUsername());
        assertEquals("CCPROG3", response.getGrade().getCourseCode());
        assertEquals(1, countGrades("12012345", "CCPROG3", "2026-T1"));

        var forbidden = assertThrows(StatusRuntimeException.class,
            () -> clientFor("fac002", "FACULTY").editGrade(EditGradeRequest.newBuilder()
                .setGradeId(gradeId).setGrade("3.0").build()));
        assertEquals(Status.Code.NOT_FOUND, forbidden.getStatus().getCode());
        assertEquals("4.0", gradeValue(gradeId));
    }

    @Test
    void rejectsInvalidEditGradeAndStudentCannotManageGrades() throws Exception {
        long gradeId = seedGrade("12012345", "CCPROG3", "2026-T1", "3.5", "fac001");
        var invalid = assertThrows(StatusRuntimeException.class,
            () -> clientFor("fac001", "FACULTY").editGrade(EditGradeRequest.newBuilder()
                .setGradeId(gradeId).setGrade("1.7").build()));
        assertEquals(Status.Code.INVALID_ARGUMENT, invalid.getStatus().getCode());

        var student = clientFor("12012345", "STUDENT");
        var listDenied = assertThrows(StatusRuntimeException.class,
            () -> student.listUploadedGrades(ListUploadedGradesRequest.getDefaultInstance()));
        assertEquals(Status.Code.PERMISSION_DENIED, listDenied.getStatus().getCode());
        var editDenied = assertThrows(StatusRuntimeException.class,
            () -> student.editGrade(EditGradeRequest.newBuilder().setGradeId(gradeId).setGrade("4.0").build()));
        assertEquals(Status.Code.PERMISSION_DENIED, editDenied.getStatus().getCode());
        var deleteDenied = assertThrows(StatusRuntimeException.class,
            () -> student.deleteGrade(DeleteGradeRequest.newBuilder().setGradeId(gradeId).build()));
        assertEquals(Status.Code.PERMISSION_DENIED, deleteDenied.getStatus().getCode());
    }

    @Test
    void deleteUsesGradeIdAndPreservesOtherRecords() throws Exception {
        long target = seedGrade("12012345", "CCPROG3", "2026-T1", "3.5", "fac001");
        long otherStudent = seedGrade("12012346", "CSARCH2", "2026-T1", "4.0", "fac001");
        long otherFaculty = seedGrade("12012347", "STDISCM", "2026-T2", "3.0", "fac002");

        var response = clientFor("fac001", "FACULTY").deleteGrade(DeleteGradeRequest.newBuilder().setGradeId(target).build());

        assertEquals(true, response.getDeleted());
        assertEquals(0, countById(target));
        assertEquals(1, countById(otherStudent));
        assertEquals(1, countById(otherFaculty));
        var forbidden = assertThrows(StatusRuntimeException.class,
            () -> clientFor("fac001", "FACULTY").deleteGrade(DeleteGradeRequest.newBuilder().setGradeId(otherFaculty).build()));
        assertEquals(Status.Code.NOT_FOUND, forbidden.getStatus().getCode());
        assertEquals(1, countById(otherFaculty));
    }

    @Test
    void managementMethodsRejectUnauthenticatedRequests() {
        var client = GradeUploadServiceGrpc.newBlockingStub(channel);
        var error = assertThrows(StatusRuntimeException.class,
            () -> client.listUploadedGrades(ListUploadedGradesRequest.getDefaultInstance()));
        assertEquals(Status.Code.UNAUTHENTICATED, error.getStatus().getCode());
    }

    private void assertInvalid(GradeUploadServiceGrpc.GradeUploadServiceBlockingStub client, UploadGradeRequest request) {
        var error = assertThrows(StatusRuntimeException.class, () -> client.uploadGrade(request));
        assertEquals(Status.Code.INVALID_ARGUMENT, error.getStatus().getCode());
    }

    private UploadGradeRequest grade(String term, String value) {
        return UploadGradeRequest.newBuilder().setStudentUsername("12012345").setCourseCode("CCPROG3").setTerm(term).setGrade(value).build();
    }

    private long seedGrade(String student, String course, String term, String grade, String uploadedBy) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO grades (student_username, course_code, term, grade, uploaded_by) VALUES (?, ?, ?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, student);
            statement.setString(2, course);
            statement.setString(3, term);
            statement.setString(4, grade);
            statement.setString(5, uploadedBy);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int countGrades(String student, String course, String term) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM grades WHERE student_username='" + student + "' AND course_code='" + course + "' AND term='" + term + "'")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private int countById(long id) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM grades WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String gradeValue(long id) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT grade FROM grades WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
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
