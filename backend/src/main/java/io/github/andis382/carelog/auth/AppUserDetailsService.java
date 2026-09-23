package io.github.andis382.carelog.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    /** Removed members keep their history but can no longer sign in. */
    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email.trim())
            .filter(User::isActive)
            .map(AppUserDetails::new)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
