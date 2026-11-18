package edu.dlsu.enrollment.security;

public enum Role {
    STUDENT,
    FACULTY;

    public static boolean isValid(String role) {
        if (role == null) {
            return false;
        }
        try {
            valueOf(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
