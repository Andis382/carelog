package io.github.andis382.carelog.today;

import io.github.andis382.carelog.daily.DailyController.JournalView;
import io.github.andis382.carelog.vitals.VitalService.RangeView;
import io.github.andis382.carelog.vitals.VitalService.ReadingView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Everything the daily card shows, in one response so a tired carer's phone makes one call. */
public record TodayView(
    LocalDate date,
    LocalDate today,
    Instant now,
    int graceMinutes,
    ElderCard elder,
    List<DutyView> duty,
    CheckInView myCheckIn,
    List<DoseLineView> doses,
    List<AsNeededView> asNeeded,
    List<VitalCard> vitals,
    Map<String, MealMark> meals,
    int glasses,
    ScoreMark mood,
    ScoreMark pain,
    List<JournalView> journal,
    AppointmentView nextAppointment,
    List<String> lowSupplies,
    int swapsForMe,
    boolean canRecord) {

    public record ElderCard(String fullName, String firstName, Integer age, String town, String photoUrl, String conditions,
                            String allergies) {}

    public record DutyView(Long userId, String name, String start, String end, String kind, boolean now) {}

    public record CheckInView(Long id, Instant checkedInAt) {}

    public record EventView(Long id, String status, Long byId, String by, Instant at, String note, boolean canUndo) {}

    public record DoseLineView(Long medicationId, String name, String strength, String doseText, String instructions, String time,
                               String state, boolean givenLate, long minutesLate, EventView event) {}

    public record AsNeededView(Long medicationId, String name, String strength, String doseText, String instructions,
                               EventView last) {}

    public record VitalCard(String kind, ReadingView last, RangeView range) {}

    public record MealMark(String amount, String by, Instant at) {}

    public record ScoreMark(int score, String by, Instant at) {}

    public record AppointmentView(LocalDate date, String time, String doctorName, String specialty, String place, long daysAway) {}
}
