package io.github.andis382.carelog.supplies;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/supplies")
public class SupplyController {

    private final SupplyService supplies;
    private final CircleService circle;
    private final CurrentUser currentUser;

    public SupplyController(SupplyService supplies, CircleService circle, CurrentUser currentUser) {
        this.supplies = supplies;
        this.circle = circle;
        this.currentUser = currentUser;
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String name, SupplyStatus status) {}

    public record StatusRequest(@NotNull SupplyStatus status) {}

    public record SupplyView(Long id, String name, String status, String updatedBy, Instant updatedAt) {
        static SupplyView of(Supply s, Map<Long, String> names) {
            return new SupplyView(s.getId(), s.getName(), s.getStatus().name(), names.get(s.getUpdatedBy()), s.getUpdatedAt());
        }
    }

    public record MarkedView(SupplyView supply, List<String> notified) {}

    @GetMapping
    public List<SupplyView> list() {
        Long orgId = currentUser.organizationId();
        Map<Long, String> names = circle.names(orgId);
        return supplies.list(orgId).stream().map(s -> SupplyView.of(s, names)).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplyView create(@Valid @RequestBody CreateRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        return SupplyView.of(supplies.create(orgId, currentUser.id(), req.name(), req.status()), circle.names(orgId));
    }

    @PutMapping("/{id}/status")
    public MarkedView mark(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        currentUser.requireRecorder();
        Long orgId = currentUser.organizationId();
        SupplyService.Marked marked = supplies.mark(orgId, currentUser.id(), id, req.status());
        return new MarkedView(SupplyView.of(marked.supply(), circle.names(orgId)), marked.notified());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        currentUser.requirePlanner();
        supplies.delete(currentUser.organizationId(), id);
    }
}
