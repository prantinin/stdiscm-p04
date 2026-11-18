package edu.dlsu.enrollment.security;

import io.grpc.Context;

public final class AuthContext {
    private static final Context.Key<AuthUser> USER_KEY =
        Context.key("auth-user");

    private AuthContext() {
    }

    public static Context.Key<AuthUser> key() {
        return USER_KEY;
    }

    public static AuthUser current() {
        return USER_KEY.get();
    }

    public static boolean isAuthenticated() {
        return current() != null;
    }

    public static void requireRole(String role) {
        AuthUser user = current();
        if (user == null) {
            throw io.grpc.Status.UNAUTHENTICATED
                .withDescription("Missing or invalid token")
                .asRuntimeException();
        }
        user.requireRole(role);
    }
}
