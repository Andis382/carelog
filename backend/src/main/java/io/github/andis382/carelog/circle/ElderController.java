package io.github.andis382.carelog.circle;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleController.ElderView;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.common.Phones;
import io.github.andis382.carelog.config.AppProperties;
import io.github.andis382.carelog.files.FileStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Year;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The elder's profile. Created in onboarding, edited from the Circle page by owner or family. */
@RestController
public class ElderController {

    private static final int MAX_CONTACTS = 5;

    private final ElderRepository elders;
    private final FileStorage files;
    private final CircleTime time;
    private final CurrentUser currentUser;
    private final AppProperties props;

    public ElderController(ElderRepository elders, FileStorage files, CircleTime time, CurrentUser currentUser,
                           AppProperties props) {
        this.elders = elders;
        this.files = files;
        this.time = time;
        this.currentUser = currentUser;
        this.props = props;
    }

    public record ContactRequest(@NotBlank @Size(max = 160) String name, @Size(max = 80) String relation,
                                 @Size(max = 40) String phone) {}

    public record ElderRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Min(1900) @Max(2100) Integer birthYear,
        @Size(max = 2000) String conditions,
        @Size(max = 1000) String allergies,
        @Size(max = 160) String gpName,
        @Size(max = 40) String gpPhone,
        @Size(max = 300) String address,
        @Size(max = 120) String town,
        @Size(max = 36) String photoFileId,
        List<@Valid ContactRequest> contacts) {}

    @PutMapping("/api/elder")
    @Transactional
    public ElderView save(@Valid @RequestBody ElderRequest req) {
        currentUser.requirePlanner();
        Long orgId = currentUser.organizationId();
        if (req.birthYear() != null && req.birthYear() > Year.now().getValue()) {
            throw ApiException.field("birthYear", "elder.birth_year");
        }
        if (req.contacts() != null && req.contacts().size() > MAX_CONTACTS) {
            throw ApiException.field("contacts", "elder.too_many_contacts");
        }
        Elder elder = elders.findByOrganizationId(orgId).orElseGet(() -> new Elder(orgId, req.fullName().trim()));
        elder.setFullName(req.fullName().trim());
        elder.setBirthYear(req.birthYear());
        elder.setConditions(trim(req.conditions()));
        elder.setAllergies(trim(req.allergies()));
        elder.setGpName(trim(req.gpName()));
        elder.setGpPhone(Phones.normalize(req.gpPhone(), props.getDefaultCountryCode()));
        elder.setAddress(trim(req.address()));
        elder.setTown(trim(req.town()));
        elder.setPhotoFileId(files.owned(orgId, req.photoFileId(), "photoFileId"));
        elder.getContacts().clear();
        if (req.contacts() != null) {
            req.contacts().forEach(c -> elder.getContacts().add(new EmergencyContact(c.name().trim(), trim(c.relation()),
                Phones.normalize(c.phone(), props.getDefaultCountryCode()))));
        }
        elder.setUpdatedAt(time.now());
        return ElderView.of(elders.save(elder), time.today(orgId).getYear());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
