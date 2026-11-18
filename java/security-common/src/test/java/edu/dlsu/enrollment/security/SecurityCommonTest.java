package edu.dlsu.enrollment.security;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import io.grpc.ServerInterceptors;
import io.grpc.stub.MetadataUtils;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityCommonTest {
    @Test
    void verifiesValidJwtAndIdentityClaims() throws Exception {
        KeyPair keys = generateKeys();
        JwtVerifier verifier = new JwtVerifier(writePublicKey(keys.getPublic()));
        String token = sign(keys, "12012345", "STUDENT", "Juan Dela Cruz", 60);

        AuthUser user = verifier.verify(token);

        assertEquals("12012345", user.username());
        assertEquals("STUDENT", user.role());
        assertEquals("Juan Dela Cruz", user.name());
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        KeyPair keys = generateKeys();
        JwtVerifier verifier = new JwtVerifier(writePublicKey(keys.getPublic()));
        String token = sign(keys, "12012345", "STUDENT", "Juan Dela Cruz", -60);

        assertThrows(JwtException.class, () -> verifier.verify(token));
    }

    @Test
    void rejectsTamperedJwt() throws Exception {
        KeyPair keys = generateKeys();
        JwtVerifier verifier = new JwtVerifier(writePublicKey(keys.getPublic()));
        String token = sign(keys, "12012345", "STUDENT", "Juan Dela Cruz", 60);

        assertThrows(JwtException.class,
            () -> verifier.verify(token.substring(0, token.lastIndexOf('.') + 1) + "tampered"));
    }

    @Test
    void interceptorRejectsMissingHeader() throws Exception {
        KeyPair keys = generateKeys();
        Server server = startServer(keys, "STUDENT");

        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName(server))
                .directExecutor()
                .build();
        try {
            StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingCall(channel, null));
            assertEquals(Status.UNAUTHENTICATED.getCode(), error.getStatus().getCode());
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    @Test
    void interceptorRejectsWrongRole() throws Exception {
        KeyPair keys = generateKeys();
        Server server = startServer(keys, "STUDENT");
        String token = sign(keys, "fac001", "FACULTY", "Dr. Elena Cruz", 60);

        ManagedChannel channel = InProcessChannelBuilder
                .forName(serverName(server))
                .directExecutor()
                .intercept(MetadataUtils.newAttachHeadersInterceptor(
                    bearerHeader("Bearer " + token)))
                .build();
        try {
            StatusRuntimeException error = assertThrows(StatusRuntimeException.class,
                () -> blockingCall(channel, "request"));
            assertEquals(Status.PERMISSION_DENIED.getCode(), error.getStatus().getCode());
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    private String sign(KeyPair keys, String subject, String role, String name, int expirySeconds) {
        Date now = new Date();
        return Jwts.builder()
            .subject(subject)
            .issuer(JwtVerifier.ISSUER)
            .claim("role", role)
            .claim("name", name)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirySeconds * 1000L))
            .signWith(keys.getPrivate(), Jwts.SIG.RS256)
            .compact();
    }

    private KeyPair generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path writePublicKey(PublicKey publicKey) throws IOException {
        RSAPublicKey rsa = (RSAPublicKey) publicKey;
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
            .encodeToString(rsa.getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + base64
            + "\n-----END PUBLIC KEY-----\n";
        Path file = Files.createTempFile("security-common-public-", ".pem");
        Files.writeString(file, pem, StandardCharsets.UTF_8);
        file.toFile().deleteOnExit();
        return file;
    }

    private static Metadata bearerHeader(String value) {
        Metadata headers = new Metadata();
        headers.put(
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER),
            value
        );
        return headers;
    }

    private static final String SERVER_PREFIX = "security-common-test-";
    private static int serverCounter;

    private Server startServer(KeyPair keys, String allowedRole) throws Exception {
        String name = SERVER_PREFIX + serverCounter++;
        MethodDescriptor<byte[], byte[]> method = MethodDescriptor
            .<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                "security.test", "Call"))
            .setRequestMarshaller(new ByteMarshaller())
            .setResponseMarshaller(new ByteMarshaller())
            .build();

        ServerServiceDefinition service = ServerServiceDefinition
            .builder(method.getServiceName())
            .addMethod(method, ServerCalls.asyncUnaryCall(
                (request, responseObserver) ->
                    responseObserver.onNext("ok".getBytes(StandardCharsets.UTF_8))))
            .build();

        Server server = InProcessServerBuilder
            .forName(name)
            .directExecutor()
            .addService(ServerInterceptors.intercept(
                service,
                new AuthInterceptor(
                    new JwtVerifier(writePublicKey(keys.getPublic())),
                    allowedRole)))
            .build()
            .start();

        serverMap.put(server, name);
        return server;
    }

    private final java.util.Map<Server, String> serverMap =
        new java.util.concurrent.ConcurrentHashMap<>();

    private String serverName(Server server) {
        return serverMap.remove(server);
    }

    private static String blockingCall(ManagedChannel channel, String value) {
        MethodDescriptor<byte[], byte[]> method = method();
        byte[] request = value == null
            ? new byte[0]
            : value.getBytes(StandardCharsets.UTF_8);
        byte[] response = ClientCalls.blockingUnaryCall(
            channel,
            method,
            CallOptions.DEFAULT,
            request);
        return new String(response, StandardCharsets.UTF_8);
    }

    private static MethodDescriptor<byte[], byte[]> method() {
        return MethodDescriptor
            .<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                "security.test", "Call"))
            .setRequestMarshaller(new ByteMarshaller())
            .setResponseMarshaller(new ByteMarshaller())
            .build();
    }

    private static final class ByteMarshaller
        implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
