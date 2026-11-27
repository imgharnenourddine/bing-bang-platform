package ma.enset.bigbang.auth;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class SessionPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private final UUID id;
    private final String username;
    private final Role role;
    private final boolean enabled;
    private String password;
    public SessionPrincipal(UUID id, String username, Role role, boolean enabled, String password) {
        this.id = id; this.username = username; this.role = role; this.enabled = enabled; this.password = password;
    }
    public UUID id() { return id; }
    public Role role() { return role; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public void eraseCredentials() { password = null; }
}
