package io.github.andis382.carelog.vitals;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VitalRangeRepository extends JpaRepository<VitalRange, Long> {

    List<VitalRange> findByOrganizationId(Long organizationId);
}
