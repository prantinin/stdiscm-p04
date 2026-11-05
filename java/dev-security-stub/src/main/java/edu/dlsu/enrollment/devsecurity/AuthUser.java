package edu.dlsu.enrollment.devsecurity;

/** Temporary development-only user identity. Replace this module with M2's security-common. */
public record AuthUser(String username, String role, String name) {
    public void requireRole(String expected) {
        if (!expected.equals(role)) throw new IllegalStateException("PERMISSION_DENIED");
    }
}
