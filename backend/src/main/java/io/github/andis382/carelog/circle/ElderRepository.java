package io.github.andis382.carelog.circle;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElderRepository extends JpaRepository<Elder, Long> {

    Optional<Elder> findByOrganizationId(Long organizationId);
}
