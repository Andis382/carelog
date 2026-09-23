package io.github.andis382.carelog.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /** Everyone who ever belonged to the circle, removed members included (for author names). */
    List<User> findByOrganizationIdOrderByNameAsc(Long organizationId);

    List<User> findByOrganizationIdAndRemovedAtIsNullOrderByNameAsc(Long organizationId);

    List<User> findByOrganizationIdAndRoleAndRemovedAtIsNull(Long organizationId, Role role);

    long countByOrganizationIdAndRemovedAtIsNull(Long organizationId);

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<User> findByIdAndOrganizationIdAndRemovedAtIsNull(Long id, Long organizationId);
}
