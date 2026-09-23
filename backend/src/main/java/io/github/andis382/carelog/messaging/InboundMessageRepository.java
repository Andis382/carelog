package io.github.andis382.carelog.messaging;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundMessageRepository extends JpaRepository<InboundMessage, Long> {

    boolean existsByProviderMessageId(String providerMessageId);

    List<InboundMessage> findByOrganizationIdOrderByReceivedAtDesc(Long organizationId, Pageable page);
}
