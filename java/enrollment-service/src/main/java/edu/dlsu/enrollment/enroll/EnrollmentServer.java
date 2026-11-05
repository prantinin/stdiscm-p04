package edu.dlsu.enrollment.enroll;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;

public final class EnrollmentServer {
  public static void main(String[] args) throws Exception {
    HikariDataSource ds = dataSource();
    Server server = ServerBuilder.forPort(Integer.parseInt(env("GRPC_PORT", "50053")))
        .addService(ServerInterceptors.intercept(new EnrollmentServiceImpl(ds), new DevAuthInterceptor())).build().start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.shutdown(); ds.close(); }));
    System.out.println("Enrollment gRPC server listening on " + server.getPort());
    server.awaitTermination();
  }
  static HikariDataSource dataSource() {
    HikariConfig c = new HikariConfig();
    c.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/course_db"));
    c.setUsername(env("DB_USER", "enrollment_user")); c.setPassword(env("DB_PASSWORD", "enrollment_password"));
    c.setMaximumPoolSize(Integer.parseInt(env("DB_POOL_SIZE", "5"))); c.setMinimumIdle(1);
    c.setPoolName("enrollment-pool"); return new HikariDataSource(c);
  }
  static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
}
