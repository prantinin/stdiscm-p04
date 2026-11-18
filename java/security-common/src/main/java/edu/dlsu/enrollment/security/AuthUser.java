package edu.dlsu.enrollment.security;

import io.grpc.Status;

public record AuthUser(String username, String role, String name) {
    public void requireRole(String expectedRole) {
        if (expectedRole == null || !expectedRole.equals(role)) {
            throw Status.PERMISSION_DENIED
                .withDescription("Required role: " + expectedRole)
                .asRuntimeException();
        }
    }

    public boolean hasRole(String expectedRole) {
        return expectedRole != null && expectedRole.equals(role);
    }
}
