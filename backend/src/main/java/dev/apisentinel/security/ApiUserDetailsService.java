package dev.apisentinel.security;

import dev.apisentinel.domain.AppUser;
import dev.apisentinel.repository.AppUserRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ApiUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public ApiUserDetailsService(AppUserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmailIgnoreCase(username)
            .map(this::principal)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    public ApiUserPrincipal loadById(UUID id) {
        return users.findById(id).map(this::principal)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    private ApiUserPrincipal principal(AppUser user) {
        return new ApiUserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getTokenVersion());
    }
}
