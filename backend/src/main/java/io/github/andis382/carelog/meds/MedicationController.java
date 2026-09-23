package io.github.andis382.carelog.meds;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationDetail;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationRequest;
import io.github.andis382.carelog.meds.MedicationDtos.MedicationView;
import io.github.andis382.carelog.meds.MedicationDtos.StopRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medications;
    private final CurrentUser currentUser;

    public MedicationController(MedicationService medications, CurrentUser currentUser) {
        this.medications = medications;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<MedicationView> list() {
        return medications.list(currentUser.organizationId());
    }

    @GetMapping("/{id}")
    public MedicationDetail detail(@PathVariable Long id) {
        return medications.detail(currentUser.organizationId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationView create(@Valid @RequestBody MedicationRequest req) {
        currentUser.requirePlanner();
        return medications.create(currentUser.organizationId(), currentUser.id(), req);
    }

    @PutMapping("/{id}")
    public MedicationView update(@PathVariable Long id, @Valid @RequestBody MedicationRequest req) {
        currentUser.requirePlanner();
        return medications.update(currentUser.organizationId(), currentUser.id(), id, req);
    }

    @PostMapping("/{id}/stop")
    public MedicationView stop(@PathVariable Long id, @Valid @RequestBody StopRequest req) {
        currentUser.requirePlanner();
        return medications.stop(currentUser.organizationId(), currentUser.id(), id, req.reason());
    }
}
