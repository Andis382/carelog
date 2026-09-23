package io.github.andis382.carelog.alerts;

import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.Elder;
import io.github.andis382.carelog.circle.ElderRepository;
import io.github.andis382.carelog.common.Texts;
import io.github.andis382.carelog.config.AppProperties;
import io.github.andis382.carelog.messaging.Messenger;
import io.github.andis382.carelog.messaging.Messenger.Outgoing;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The WhatsApp messages the circle sends to its own members, each in the reader's language.
 * Members without a phone number are skipped; the app shows the same information anyway.
 */
@Service
public class CircleNotifier {

    private final Messenger messenger;
    private final ElderRepository elders;
    private final CircleService circle;
    private final Texts texts;
    private final AppProperties props;

    public CircleNotifier(Messenger messenger, ElderRepository elders, CircleService circle, Texts texts, AppProperties props) {
        this.messenger = messenger;
        this.elders = elders;
        this.circle = circle;
        this.texts = texts;
        this.props = props;
    }

    /** Tells every coordinator that a supply is low or out. Returns who was written to. */
    public List<String> supplyLow(Long orgId, Long supplyId, String supply, boolean out, User by) {
        List<String> told = new ArrayList<>();
        for (User coordinator : circle.coordinators(orgId)) {
            if (coordinator.getPhone() == null || coordinator.getId().equals(by.getId())) {
                continue;
            }
            String locale = coordinator.getLocale();
            messenger.send(new Outgoing(orgId, coordinator.getPhone(), coordinator.getName(), "supplies_low", locale,
                Map.of("name", coordinator.firstName(), "by", by.firstName(), "supply", supply,
                    "state", texts.in(locale, out ? "supply.state.OUT" : "supply.state.RUNNING_LOW"),
                    "elder", elderName(orgId)),
                props.link("/supplies"), "supply", supplyId));
            told.add(coordinator.getName());
        }
        return told;
    }

    public boolean swapRequested(Long orgId, Long swapId, User from, User to, LocalDate date, LocalTime start, LocalTime end,
                                 String message) {
        if (to.getPhone() == null) {
            return false;
        }
        String locale = to.getLocale();
        String note = message == null || message.isBlank() ? "" : "“" + message.trim() + "”";
        messenger.send(new Outgoing(orgId, to.getPhone(), to.getName(), "shift_swap_request", locale,
            Map.of("name", to.firstName(), "from", from.firstName(), "date", day(date, locale),
                "time", hhmm(start) + "–" + hhmm(end), "elder", elderName(orgId), "message", note),
            props.link("/rota"), "swap", swapId));
        return true;
    }

    /** A dose is over two hours late and nobody has recorded it. */
    public int doseMissed(Long orgId, Long medicationId, String medicine, LocalTime time) {
        int sent = 0;
        for (User coordinator : circle.coordinators(orgId)) {
            if (coordinator.getPhone() == null) {
                continue;
            }
            messenger.send(new Outgoing(orgId, coordinator.getPhone(), coordinator.getName(), "dose_missed",
                coordinator.getLocale(), Map.of("elder", elderName(orgId), "time", hhmm(time), "medicine", medicine),
                props.link("/"), "medication", medicationId));
            sent++;
        }
        return sent;
    }

    public String elderName(Long orgId) {
        return elders.findByOrganizationId(orgId).map(Elder::firstName).orElse("");
    }

    static String day(LocalDate date, String locale) {
        return DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag(locale)).format(date);
    }

    static String hhmm(LocalTime time) {
        return DateTimeFormatter.ofPattern("HH:mm").format(time);
    }
}
