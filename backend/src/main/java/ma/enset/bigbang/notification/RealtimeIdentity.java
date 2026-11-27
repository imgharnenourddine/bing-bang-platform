package ma.enset.bigbang.notification;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;
import ma.enset.bigbang.auth.Role;

/** Namespaced identity for STOMP routing; never use a public participant number. */
public record RealtimeIdentity(Role role, UUID id) implements Principal, Serializable {
    @Override public String getName() { return name(role, id); }
    public static String name(Role role, UUID id) { return role.name() + ":" + id; }
}
