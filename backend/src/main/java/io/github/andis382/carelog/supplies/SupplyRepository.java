package io.github.andis382.carelog.supplies;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyRepository extends JpaRepository<Supply, Long> {

    List<Supply> findByOrganizationIdOrderByNameAsc(Long organizationId);

    Optional<Supply> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
