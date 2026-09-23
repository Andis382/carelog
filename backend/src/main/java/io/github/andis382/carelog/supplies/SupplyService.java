package io.github.andis382.carelog.supplies;

import io.github.andis382.carelog.alerts.CircleNotifier;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The supplies list. Marking something low or out writes to the coordinator on WhatsApp. */
@Service
public class SupplyService {

    private final SupplyRepository supplies;
    private final SupplyEventRepository events;
    private final UserRepository users;
    private final CircleNotifier notifier;
    private final CircleTime time;

    public SupplyService(SupplyRepository supplies, SupplyEventRepository events, UserRepository users, CircleNotifier notifier,
                         CircleTime time) {
        this.supplies = supplies;
        this.events = events;
        this.users = users;
        this.notifier = notifier;
        this.time = time;
    }

    public record Marked(Supply supply, List<String> notified) {}

    /** What needs buying first, then the rest by name. */
    public List<Supply> list(Long orgId) {
        return supplies.findByOrganizationIdOrderByNameAsc(orgId).stream()
            .sorted(Comparator.comparing((Supply s) -> -s.getStatus().ordinal()))
            .toList();
    }

    @Transactional
    public Supply create(Long orgId, Long userId, String name, SupplyStatus status) {
        String trimmed = name.trim();
        if (supplies.existsByOrganizationIdAndNameIgnoreCase(orgId, trimmed)) {
            throw ApiException.field("name", "supply.exists");
        }
        Instant now = time.now();
        Supply supply = supplies.save(new Supply(orgId, trimmed, status == null ? SupplyStatus.OK : status, userId, now));
        events.save(new SupplyEvent(orgId, supply.getId(), supply.getStatus(), userId, now));
        return supply;
    }

    @Transactional
    public Marked mark(Long orgId, Long userId, Long id, SupplyStatus status) {
        Supply supply = supplies.findByIdAndOrganizationId(id, orgId).orElseThrow(ApiException::notFound);
        SupplyStatus before = supply.getStatus();
        if (before == status) {
            return new Marked(supply, List.of());
        }
        Instant now = time.now();
        supply.mark(status, userId, now);
        events.save(new SupplyEvent(orgId, supply.getId(), status, userId, now));
        boolean worse = status.ordinal() > before.ordinal();
        if (!worse || !status.needsBuying()) {
            return new Marked(supply, List.of());
        }
        User by = users.findById(userId).orElseThrow();
        return new Marked(supply, notifier.supplyLow(orgId, supply.getId(), supply.getName(), status == SupplyStatus.OUT, by));
    }

    @Transactional
    public void delete(Long orgId, Long id) {
        supplies.delete(supplies.findByIdAndOrganizationId(id, orgId).orElseThrow(ApiException::notFound));
    }
}
