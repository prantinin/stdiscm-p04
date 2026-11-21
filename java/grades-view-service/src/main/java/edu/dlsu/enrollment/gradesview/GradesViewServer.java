package edu.dlsu.enrollment.gradesview;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.security.AuthInterceptor;
import edu.dlsu.enrollment.security.JwtVerifier;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;

public final class GradesViewServer {
  public static void main(String[] args) throws Exception {
    HikariDataSource ds = dataSource();
    Server server = ServerBuilder.forPort(Integer.parseInt(env("GRPC_PORT", "50054")))
        .addService(ServerInterceptors.intercept(new GradesViewServiceImpl(ds), new AuthInterceptor(new JwtVerifier()))).build().start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.shutdown(); ds.close(); }));
    System.out.println("Grades View gRPC server listening on " + server.getPort()); server.awaitTermination();
  }
  static HikariDataSource dataSource() {
    HikariConfig c = new HikariConfig(); c.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/grades_db"));
    c.setUsername(env("DB_USER", "grades_view_user")); c.setPassword(env("DB_PASSWORD", "grades_view_password"));
    c.setMaximumPoolSize(Integer.parseInt(env("DB_POOL_SIZE", "5"))); c.setMinimumIdle(1); c.setPoolName("grades-view-pool"); return new HikariDataSource(c);
  }
  static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
}
