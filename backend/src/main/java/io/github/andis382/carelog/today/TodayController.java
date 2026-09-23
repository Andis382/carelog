package io.github.andis382.carelog.today;

import io.github.andis382.carelog.auth.CurrentUser;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TodayController {

    private final TodayService today;
    private final CurrentUser currentUser;

    public TodayController(TodayService today, CurrentUser currentUser) {
        this.today = today;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/today")
    public TodayView day(@RequestParam(required = false) LocalDate date) {
        return today.day(currentUser.organizationId(), currentUser.id(), currentUser.role(), date);
    }
}
