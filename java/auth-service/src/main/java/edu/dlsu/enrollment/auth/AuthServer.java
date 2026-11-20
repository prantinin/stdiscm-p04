package edu.dlsu.enrollment.auth;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class AuthServer {
    private AuthServer() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        HikariDataSource dataSource = createDataSource();
        Server server;
        try {
            int port = Integer.parseInt(environment("GRPC_PORT", "50051"));
            server = ServerBuilder.forPort(port)
                .addService(new AuthServiceImpl(dataSource))
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();
        } catch (RuntimeException | IOException e) {
            dataSource.close();
            throw e;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                dataSource.close();
            }
        }, "auth-shutdown"));

        System.out.println("Auth gRPC server listening on " + server.getPort());
        server.awaitTermination();
    }

    private static HikariDataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(environment("DB_URL", "jdbc:mysql://localhost:3306/auth_db"));
        config.setUsername(environment("DB_USER", "auth_user"));
        config.setPassword(environment("DB_PASSWORD", "auth_password"));
        config.setMaximumPoolSize(Integer.parseInt(environment("DB_POOL_SIZE", "5")));
        config.setMinimumIdle(1);
        config.setPoolName("auth-pool");
        return new HikariDataSource(config);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
