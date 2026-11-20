package edu.dlsu.enrollment.enroll;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import enrollment.enroll.*;
import io.grpc.*;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class EnrollmentServiceTest {
  private HikariDataSource ds; private Server server; private ManagedChannel channel;
  private KeyPair keys; private Path publicKeyPath;
  @BeforeEach void start() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    keys = generator.generateKeyPair();
    publicKeyPath = Files.createTempFile("enrollment-test-public-", ".pem");
    String publicKey = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(keys.getPublic().getEncoded());
    Files.writeString(publicKeyPath, "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----\n", StandardCharsets.UTF_8);
    HikariConfig config=new HikariConfig(); config.setJdbcUrl("jdbc:h2:mem:enrollment" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1"); config.setUsername("sa"); config.setMaximumPoolSize(4); ds=new HikariDataSource(config);
    try(Connection c=ds.getConnection(); Statement s=c.createStatement()) {
      s.execute("CREATE TABLE courses (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(16), name VARCHAR(100), section VARCHAR(8), schedule VARCHAR(50), capacity INT, status VARCHAR(10), faculty_name VARCHAR(100), UNIQUE(code,section))");
      s.execute("CREATE TABLE enrollments (id BIGINT AUTO_INCREMENT PRIMARY KEY, student_username VARCHAR(32), course_id BIGINT, enrolled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(student_username,course_id))");
      s.execute("INSERT INTO courses(code,name,section,schedule,capacity,status,faculty_name) VALUES ('ONE','One Seat','A','MW',1,'OPEN','Prof'),('CLOSED','Closed','A','TTH',3,'CLOSED','Prof')");
    }
    String name=InProcessServerBuilder.generateName(); server=InProcessServerBuilder.forName(name).directExecutor().addService(ServerInterceptors.intercept(new EnrollmentServiceImpl(ds),new AuthInterceptor(new JwtVerifier(publicKeyPath)))).build().start(); channel=InProcessChannelBuilder.forName(name).directExecutor().build();
  }
  @AfterEach void stop() throws Exception { channel.shutdownNow().awaitTermination(2,TimeUnit.SECONDS); server.shutdownNow().awaitTermination(2,TimeUnit.SECONDS); ds.close(); Files.deleteIfExists(publicKeyPath); }
  private EnrollmentServiceGrpc.EnrollmentServiceBlockingStub student(String username) {
    Metadata h=new Metadata(); h.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token(username,"STUDENT"));
    return EnrollmentServiceGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(h));
  }
  private EnrollmentServiceGrpc.EnrollmentServiceBlockingStub faculty(String username) {
    Metadata h=new Metadata(); h.put(Metadata.Key.of("authorization",Metadata.ASCII_STRING_MARSHALLER),"Bearer "+token(username,"FACULTY"));
    return EnrollmentServiceGrpc.newBlockingStub(channel).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(h));
  }
  private String token(String username,String role) {
    Date now=new Date();
    return Jwts.builder().subject(username).issuer(JwtVerifier.ISSUER).claim("role",role).claim("name","Test User")
        .issuedAt(now).expiration(new Date(now.getTime()+60_000)).signWith(keys.getPrivate(),Jwts.SIG.RS256).compact();
  }
  private EnrollmentServiceGrpc.EnrollmentServiceBlockingStub anonymous() { return EnrollmentServiceGrpc.newBlockingStub(channel); }
  @Test void unauthenticatedAndFacultyCallersCannotUseStudentMethods() {
    StatusRuntimeException missing=assertThrows(StatusRuntimeException.class,()->anonymous().listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance()));
    assertEquals(Status.Code.UNAUTHENTICATED,missing.getStatus().getCode());
    StatusRuntimeException faculty=assertThrows(StatusRuntimeException.class,()->faculty("fac001").listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance()));
    assertEquals(Status.Code.PERMISSION_DENIED,faculty.getStatus().getCode());
  }
  @Test void duplicateEnrollmentIsRejectedAndOnlyOwnEnrollmentsAreReturned() {
    EnrollRequest one=EnrollRequest.newBuilder().setCourseCode("ONE").setSection("A").build(); student("12012345").enrollStudent(one);
    StatusRuntimeException duplicate=assertThrows(StatusRuntimeException.class,()->student("12012345").enrollStudent(one)); assertEquals(Status.Code.ALREADY_EXISTS,duplicate.getStatus().getCode());
    assertEquals(1,student("12012345").listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance()).getRecordsCount());
    assertEquals(0,student("12067890").listMyEnrollments(ListMyEnrollmentsRequest.getDefaultInstance()).getRecordsCount());
  }
  @Test void closedCourseIsRejectedAndExactlyOneStudentGetsTheFinalSeat() throws Exception {
    StatusRuntimeException closed=assertThrows(StatusRuntimeException.class,()->student("12012345").enrollStudent(EnrollRequest.newBuilder().setCourseCode("CLOSED").setSection("A").build())); assertEquals(Status.Code.FAILED_PRECONDITION,closed.getStatus().getCode());
    ExecutorService workers=Executors.newFixedThreadPool(2); CyclicBarrier gate=new CyclicBarrier(2);
    List<Future<Boolean>> results=workers.invokeAll(List.of(() -> attempt("12012345",gate),()->attempt("12067890",gate))); workers.shutdown();
    assertEquals(1,(results.get(0).get() ? 1 : 0) + (results.get(1).get() ? 1 : 0));
  }
  private boolean attempt(String student,CyclicBarrier gate) throws Exception { gate.await(); try { student(student).enrollStudent(EnrollRequest.newBuilder().setCourseCode("ONE").setSection("A").build()); return true; } catch(StatusRuntimeException e) { assertEquals(Status.Code.FAILED_PRECONDITION,e.getStatus().getCode()); return false; } }
}
