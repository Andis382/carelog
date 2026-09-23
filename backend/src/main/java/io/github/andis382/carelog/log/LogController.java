package io.github.andis382.carelog.log;

import io.github.andis382.carelog.auth.CurrentUser;
import io.github.andis382.carelog.circle.CircleTime;
import io.github.andis382.carelog.log.LogService.Page;
import io.github.andis382.carelog.log.LogService.Type;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogController {

    private static final int MAX_DAYS = 31;

    private final LogService log;
    private final CircleTime time;
    private final CurrentUser currentUser;

    public LogController(LogService log, CircleTime time, CurrentUser currentUser) {
        this.log = log;
        this.time = time;
        this.currentUser = currentUser;
    }

    /** {@code to} is the newest day to include; the client pages backwards with {@code to = from - 1}. */
    @GetMapping("/api/log")
    public Page page(@RequestParam(required = false) LocalDate to, @RequestParam(defaultValue = "7") int days,
                     @RequestParam(required = false) Set<Type> types) {
        Long orgId = currentUser.organizationId();
        LocalDate today = time.today(orgId);
        LocalDate last = to == null || to.isAfter(today) ? today : to;
        return log.page(orgId, last, Math.max(1, Math.min(days, MAX_DAYS)), types == null ? EnumSet.allOf(Type.class) : types);
    }
}
