package io.github.andis382.carelog.demo;

import io.github.andis382.carelog.auth.Organization;
import io.github.andis382.carelog.auth.OrganizationRepository;
import io.github.andis382.carelog.auth.Role;
import io.github.andis382.carelog.auth.User;
import io.github.andis382.carelog.auth.UserRepository;
import io.github.andis382.carelog.circle.CircleService;
import io.github.andis382.carelog.circle.CircleSettings;
import io.github.andis382.carelog.circle.Elder;
import io.github.andis382.carelog.circle.ElderRepository;
import io.github.andis382.carelog.circle.EmergencyContact;
import io.github.andis382.carelog.circle.Plan;
import io.github.andis382.carelog.config.AppProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with the "Nëna Drita" circle: Drita Kola, 81, in Shkodër, her
 * daughter in Milan who pays, her son nearby, a hired carer and a neighbour, with five weeks
 * of history so every screen has something true-looking to show.
 */
@Component
@Profile("!test")
public class DemoSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL = "demo@carelog.test";
    public static final String DEMO_PASSWORD = "demo1234";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final AppProperties props;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final ElderRepository elders;
    private final CircleService circle;
    private final DemoHistory history;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoSeeder(AppProperties props, OrganizationRepository organizations, UserRepository users, ElderRepository elders,
                      CircleService circle, DemoHistory history, PasswordEncoder encoder, Clock clock) {
        this.props = props;
        this.organizations = organizations;
        this.users = users;
        this.elders = elders;
        this.circle = circle;
        this.history = history;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.isDemo() || users.count() > 0) {
            return;
        }
        Organization org = new Organization("Nëna Drita");
        org.setLocale("sq");
        org.setTimezone("Europe/Tirane");
        organizations.save(org);
        Long orgId = org.getId();

        Instant now = clock.instant();
        User elira = member(orgId, "Elira Kola", DEMO_EMAIL, Role.OWNER, "en", "355692041187", now.minus(Duration.ofDays(1)));
        User gent = member(orgId, "Gent Kola", "gent@carelog.test", Role.FAMILY, "sq", "355682234518", now.minus(Duration.ofHours(9)));
        User mira = member(orgId, "Mira Hasani", "mira@carelog.test", Role.CARER, "sq", "355673109254", now.minus(Duration.ofHours(15)));
        member(orgId, "Arta Marku", "arta@carelog.test", Role.VIEWER, "sq", "355698770431", now.minus(Duration.ofDays(3)));

        CircleSettings settings = circle.start(orgId, elira);
        settings.setPlan(Plan.FAMILY);
        settings.setDoseAlerts(true);

        Elder drita = new Elder(orgId, "Drita Kola");
        drita.setBirthYear(1945);
        drita.setTown("Shkodër");
        drita.setAddress("Rruga Kolë Idromeno 14, Shkodër");
        drita.setConditions("Diabet tip 2, tension i lartë, artrozë në të dy gjunjët. Dëgjon pak nga veshi i majtë.");
        drita.setAllergies("Penicilinë");
        drita.setGpName("Dr. Lindita Gjoni");
        drita.setGpPhone("355694127730");
        drita.getContacts().add(new EmergencyContact("Gent Kola", "djali", "355682234518"));
        drita.getContacts().add(new EmergencyContact("Elira Kola", "vajza, Milano", "355692041187"));
        drita.getContacts().add(new EmergencyContact("Arta Marku", "fqinja", "355698770431"));
        elders.save(drita);

        history.write(orgId, new DemoHistory.People(elira, gent, mira));
        log.info("Demo circle ready. Sign in with {} / {} (also gent@, mira@, arta@carelog.test)", DEMO_EMAIL, DEMO_PASSWORD);
    }

    private User member(Long orgId, String name, String email, Role role, String locale, String phone, Instant lastSeen) {
        User user = new User(orgId, name, email, encoder.encode(DEMO_PASSWORD), role);
        user.setLocale(locale);
        user.setPhone(phone);
        user.setLastLoginAt(lastSeen);
        return users.save(user);
    }
}
