package io.github.andis382.carelog.auth;

import io.github.andis382.carelog.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Who is calling. Every query for tenant data goes through {@link #organizationId()}. */
@Component
public class CurrentUser {

    private final UserRepository users;
    private final OrganizationRepository organizations;

    public CurrentUser(UserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    public AppUserDetails details() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails details)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "error.unauthenticated");
        }
        return details;
    }

    public Long id() {
        return details().getUserId();
    }

    public Long organizationId() {
        return details().getOrganizationId();
    }

    public Role role() {
        return Role.valueOf(details().getRole());
    }

    public User user() {
        return users.findById(id())
            .filter(User::isActive)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "error.unauthenticated"));
    }

    public Organization organization() {
        return organizations.findById(organizationId()).orElseThrow(ApiException::notFound);
    }

    public boolean hasRole(Role role) {
        return role == role();
    }

    public void requireRole(Role... roles) {
        Role current = role();
        for (Role role : roles) {
            if (role == current) {
                return;
            }
        }
        throw ApiException.forbidden();
    }

    /** Anyone but a viewer: the log is read-only for viewers. */
    public void requireRecorder() {
        if (!role().canRecord()) {
            throw ApiException.forbidden();
        }
    }

    /** Owner or family: medicines, other people's shifts, the elder's profile. */
    public void requirePlanner() {
        if (!role().canPlan()) {
            throw ApiException.forbidden();
        }
    }
}
