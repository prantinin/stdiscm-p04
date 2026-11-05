package edu.dlsu.enrollment.devsecurity;

import io.grpc.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEVELOPMENT ONLY: obtains sub/role/name from an unsigned JWT payload so M3 can
 * integrate with the View before M2 ships signature verification. Never deploy it.
 */
public final class DevAuthInterceptor implements ServerInterceptor {
    public static final Context.Key<AuthUser> USER = Context.key("auth-user");
    private static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    private static final Pattern CLAIM = Pattern.compile("\\\"(sub|role|name)\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");

    @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String value = headers.get(AUTHORIZATION);
        AuthUser user = parse(value);
        if (user == null) {
            call.close(Status.UNAUTHENTICATED.withDescription("Missing or invalid token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return Contexts.interceptCall(Context.current().withValue(USER, user), call, headers, next);
    }

    private static AuthUser parse(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ")) return null;
            String[] parts = header.substring(7).split("\\.");
            if (parts.length != 3) return null;
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String sub = null, role = null, name = "";
            Matcher m = CLAIM.matcher(json);
            while (m.find()) { if (m.group(1).equals("sub")) sub=m.group(2); else if (m.group(1).equals("role")) role=m.group(2); else name=m.group(2); }
            return sub == null || role == null ? null : new AuthUser(sub, role, name);
        } catch (IllegalArgumentException e) { return null; }
    }
}
