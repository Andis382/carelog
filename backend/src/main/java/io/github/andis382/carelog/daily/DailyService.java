package io.github.andis382.carelog.daily;

import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.common.ApiException;
import io.github.andis382.carelog.files.FileStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Meals, drinks and journal entries from the Today card. */
@Service
public class DailyService {

    private static final int MAX_GLASSES = 20;

    private final MealRepository meals;
    private final JournalRepository journal;
    private final FileStorage files;
    private final CircleTime time;

    public DailyService(MealRepository meals, JournalRepository journal, FileStorage files, CircleTime time) {
        this.meals = meals;
        this.journal = journal;
        this.files = files;
        this.time = time;
    }

    public record MealCommand(LocalDate date, MealSlot slot, MealAmount amount, Integer glasses, String note, String clientId,
                              Instant at) {}

    public record JournalCommand(JournalKind kind, Integer score, String text, String photoFileId, String clientId, Instant at) {}

    @Transactional
    public Meal recordMeal(Long orgId, Long userId, MealCommand cmd) {
        Optional<Meal> replay = blank(cmd.clientId()) ? Optional.empty() : meals.findByOrganizationIdAndClientId(orgId, cmd.clientId());
        if (replay.isPresent()) {
            return replay.get();
        }
        int glasses = cmd.glasses() == null ? 0 : cmd.glasses();
        if (glasses < 0 || glasses > MAX_GLASSES) {
            throw ApiException.field("glasses", "meal.glasses_range");
        }
        if (cmd.slot() == MealSlot.DRINK ? glasses == 0 : cmd.amount() == null) {
            throw ApiException.field(cmd.slot() == MealSlot.DRINK ? "glasses" : "amount", "meal.amount_required");
        }
        Instant at = time.resolveAt(cmd.at());
        LocalDate date = cmd.date() != null ? cmd.date() : at.atZone(time.zone(orgId)).toLocalDate();
        if (date.isAfter(time.today(orgId))) {
            throw ApiException.field("date", "dose.future");
        }
        MealAmount amount = cmd.slot() == MealSlot.DRINK ? null : cmd.amount();
        return meals.save(new Meal(orgId, date, cmd.slot(), amount, glasses, trim(cmd.note()), userId, at, cmd.clientId()));
    }

    @Transactional
    public JournalEntry recordJournal(Long orgId, Long userId, JournalCommand cmd) {
        Optional<JournalEntry> replay = blank(cmd.clientId())
            ? Optional.empty() : journal.findByOrganizationIdAndClientId(orgId, cmd.clientId());
        if (replay.isPresent()) {
            return replay.get();
        }
        JournalKind kind = cmd.kind();
        if (kind.needsScore() && cmd.score() == null) {
            throw ApiException.field("score", "journal.score_required");
        }
        if (cmd.score() != null && (cmd.score() < 1 || cmd.score() > 5)) {
            throw ApiException.field("score", "journal.score_range");
        }
        String text = trim(cmd.text());
        if (kind.needsText() && text == null && blank(cmd.photoFileId())) {
            throw ApiException.field("text", "journal.text_required");
        }
        String photo = files.owned(orgId, cmd.photoFileId(), "photoFileId");
        return journal.save(new JournalEntry(orgId, kind, cmd.score(), text, photo, userId, time.resolveAt(cmd.at()),
            cmd.clientId()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return blank(value) ? null : value.trim();
    }
}
