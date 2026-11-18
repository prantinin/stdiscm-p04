package edu.dlsu.enrollment.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.jsonwebtoken.JwtException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class AuthInterceptor implements ServerInterceptor {
    private static final Metadata.Key<String> AUTHORIZATION =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final JwtVerifier verifier;
    private final Set<String> allowedRoles;

    public AuthInterceptor() {
        this(new JwtVerifier());
    }

    public AuthInterceptor(JwtVerifier verifier) {
        this(verifier, (String[]) null);
    }

    public AuthInterceptor(JwtVerifier verifier, String... allowedRoles) {
        if (verifier == null) {
            throw new IllegalArgumentException("JWT verifier is required");
        }
        this.verifier = verifier;
        this.allowedRoles = allowedRoles == null
            ? Set.of()
            : Arrays.stream(allowedRoles)
                .filter(role -> role != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    public AuthInterceptor(String publicKeyPath) {
        this(new JwtVerifier(publicKeyPath));
    }

    public AuthInterceptor(String publicKeyPath, String... allowedRoles) {
        this(new JwtVerifier(publicKeyPath), allowedRoles);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
        ServerCall<ReqT, RespT> call,
        Metadata headers,
        ServerCallHandler<ReqT, RespT> next
    ) {
        String authorization = headers.get(AUTHORIZATION);
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return unauthenticated(call, "Missing or invalid bearer token");
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return unauthenticated(call, "Missing or invalid bearer token");
        }

        try {
            AuthUser user = verifier.verify(token);
            if (!allowedRoles.isEmpty() && !allowedRoles.contains(user.role())) {
                call.close(
                    Status.PERMISSION_DENIED.withDescription("Insufficient role"),
                    new Metadata()
                );
                return new ServerCall.Listener<>() { };
            }

            Context context = Context.current().withValue(AuthContext.key(), user);
            return Contexts.interceptCall(context, call, headers, next);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthenticated(call, "Missing or invalid token");
        }
    }

    private <ReqT, RespT> ServerCall.Listener<ReqT> unauthenticated(
        ServerCall<ReqT, RespT> call,
        String description
    ) {
        call.close(
            Status.UNAUTHENTICATED.withDescription(description),
            new Metadata()
        );
        return new ServerCall.Listener<>() { };
    }
}
