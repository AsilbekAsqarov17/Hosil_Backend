package uz.hosil.platform.common.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Security principal representing an authenticated user session across API endpoints.
 */
public record UserPrincipal(
        UUID userId,
        String phoneNumber,
        List<String> roles
) implements Principal, Serializable {

    @Override
    public String getName() {
        return userId.toString();
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
