package edu.dlsu.enrollment.catalog;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import edu.dlsu.enrollment.devsecurity.DevAuthInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import java.util.concurrent.TimeUnit;

public final class CatalogServer {
  public static void main(String[] args) throws Exception {
    HikariDataSource ds = dataSource();
    Server server = ServerBuilder.forPort(Integer.parseInt(env("GRPC_PORT", "50052")))
        .addService(ServerInterceptors.intercept(new CatalogServiceImpl(ds), new DevAuthInterceptor())).build().start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.shutdown(); ds.close(); }));
    System.out.println("Catalog gRPC server listening on " + server.getPort());
    server.awaitTermination();
  }
  static HikariDataSource dataSource() {
    HikariConfig c = new HikariConfig();
    c.setJdbcUrl(env("DB_URL", "jdbc:mysql://localhost:3306/course_db"));
    c.setUsername(env("DB_USER", "catalog_user")); c.setPassword(env("DB_PASSWORD", "catalog_password"));
    c.setMaximumPoolSize(Integer.parseInt(env("DB_POOL_SIZE", "5"))); c.setMinimumIdle(1);
    c.setPoolName("catalog-pool"); return new HikariDataSource(c);
  }
  static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
}
