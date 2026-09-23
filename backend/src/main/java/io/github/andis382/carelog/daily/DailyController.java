package io.github.andis382.carelog.daily;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.daily.DailyService.JournalCommand;
import io.github.andis382.carelog.daily.DailyService.MealCommand;
import io.github.andis382.carelog.files.UploadController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DailyController {

    private final DailyService daily;
    private final CircleService circle;
    private final CurrentUser currentUser;

    public DailyController(DailyService daily, CircleService circle, CurrentUser currentUser) {
        this.daily = daily;
        this.circle = circle;
        this.currentUser = currentUser;
    }

    public record MealRequest(
        LocalDate date,
        @NotNull MealSlot slot,
        MealAmount amount,
        Integer glasses,
        @Size(max = 500) String note,
        @Size(max = 64) String clientId,
        Instant at) {}

    public record JournalRequest(
        @NotNull JournalKind kind,
        Integer score,
        @Size(max = 4000) String text,
        @Size(max = 36) String photoFileId,
        @Size(max = 64) String clientId,
        Instant at) {}

    public record MealView(Long id, LocalDate date, String slot, String amount, int glasses, String note, String by,
                           Instant at) {
        static MealView of(Meal m, String by) {
            return new MealView(m.getId(), m.getMealDate(), m.getSlot().name(), m.getAmount() == null ? null : m.getAmount().name(),
                m.getGlasses(), m.getNote(), by, m.getRecordedAt());
        }
    }

    public record JournalView(Long id, String kind, Integer score, String text, String photoUrl, String by, Instant at) {
        public static JournalView of(JournalEntry j, String by) {
            return new JournalView(j.getId(), j.getKind().name(), j.getScore(), j.getBody(),
                UploadController.fileUrl(j.getPhotoFileId()), by, j.getRecordedAt());
        }
    }

    @PostMapping("/api/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView meal(@Valid @RequestBody MealRequest req) {
        currentUser.requireRecorder();
        Meal meal = daily.recordMeal(currentUser.organizationId(), currentUser.id(),
            new MealCommand(req.date(), req.slot(), req.amount(), req.glasses(), req.note(), req.clientId(), req.at()));
        return MealView.of(meal, author(meal.getRecordedBy()));
    }

    @PostMapping("/api/journal")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalView journal(@Valid @RequestBody JournalRequest req) {
        currentUser.requireRecorder();
        JournalEntry entry = daily.recordJournal(currentUser.organizationId(), currentUser.id(),
            new JournalCommand(req.kind(), req.score(), req.text(), req.photoFileId(), req.clientId(), req.at()));
        return JournalView.of(entry, author(entry.getRecordedBy()));
    }

    private String author(Long userId) {
        return circle.names(currentUser.organizationId()).get(userId);
    }
}
