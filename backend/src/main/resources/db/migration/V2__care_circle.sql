-- CareLog: the organisation is the care circle. This adds the elder, the circle's plan and
-- settings, and everything the circle records. Authors are kept for good (members are
-- deactivated, never deleted) because "who did it, and when" is the point of the log.

ALTER TABLE users ADD COLUMN phone VARCHAR(40);
ALTER TABLE users ADD COLUMN removed_at TIMESTAMPTZ;

CREATE TABLE circle_settings (
    organization_id  BIGINT PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    plan             VARCHAR(16) NOT NULL DEFAULT 'FREE',
    payer_user_id    BIGINT REFERENCES users(id),
    grace_minutes    INT         NOT NULL DEFAULT 30,
    dose_alerts      BOOLEAN     NOT NULL DEFAULT FALSE,
    weekly_summary   BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Normal ranges are for display only; nothing in the app acts on them.
CREATE TABLE vital_ranges (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    low              NUMERIC(6,1),
    high             NUMERIC(6,1),
    low2             NUMERIC(6,1),
    high2            NUMERIC(6,1),
    UNIQUE (organization_id, kind)
);

CREATE TABLE elders (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT       NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    full_name        VARCHAR(160) NOT NULL,
    birth_year       INT,
    photo_file_id    VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    conditions       TEXT,
    allergies        TEXT,
    gp_name          VARCHAR(160),
    gp_phone         VARCHAR(40),
    address          VARCHAR(300),
    town             VARCHAR(120),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE elder_contacts (
    elder_id   BIGINT       NOT NULL REFERENCES elders(id) ON DELETE CASCADE,
    position   INT          NOT NULL,
    name       VARCHAR(160) NOT NULL,
    relation   VARCHAR(80),
    phone      VARCHAR(40),
    PRIMARY KEY (elder_id, position)
);

CREATE TABLE medications (
    id                 BIGSERIAL PRIMARY KEY,
    organization_id    BIGINT       NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name               VARCHAR(160) NOT NULL,
    strength           VARCHAR(80),
    dose_text          VARCHAR(160),
    instructions       VARCHAR(500),
    frequency          VARCHAR(16)  NOT NULL,
    times              VARCHAR(200) NOT NULL DEFAULT '',
    weekdays           VARCHAR(40)  NOT NULL DEFAULT '',
    start_date         DATE         NOT NULL,
    end_date           DATE,
    active             BOOLEAN      NOT NULL DEFAULT TRUE,
    prescriber         VARCHAR(160),
    box_photo_file_id  VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    created_by         BIGINT       NOT NULL REFERENCES users(id),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    stopped_at         TIMESTAMPTZ,
    stopped_by         BIGINT       REFERENCES users(id),
    stop_reason        VARCHAR(300)
);
CREATE INDEX medications_org_idx ON medications (organization_id, active);

CREATE TABLE medication_changes (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    medication_id    BIGINT      NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    changes_json     TEXT,
    note             VARCHAR(300),
    changed_by       BIGINT      NOT NULL REFERENCES users(id),
    changed_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX medication_changes_med_idx ON medication_changes (medication_id, changed_at DESC);
CREATE INDEX medication_changes_org_idx ON medication_changes (organization_id, changed_at);

CREATE TABLE dose_events (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    medication_id    BIGINT      NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    dose_date        DATE        NOT NULL,
    scheduled_time   TIME,
    status           VARCHAR(16) NOT NULL,
    note             VARCHAR(500),
    recorded_by      BIGINT      NOT NULL REFERENCES users(id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    client_id        VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- The double-dose guard: one record per scheduled dose, enforced by the database itself.
-- "As needed" doses have no scheduled time and may be recorded several times a day.
CREATE UNIQUE INDEX dose_events_slot_uq ON dose_events (medication_id, dose_date, scheduled_time)
    WHERE scheduled_time IS NOT NULL;
CREATE UNIQUE INDEX dose_events_client_uq ON dose_events (organization_id, client_id) WHERE client_id IS NOT NULL;
CREATE INDEX dose_events_org_date_idx ON dose_events (organization_id, dose_date);

-- Remembers which late doses already raised a WhatsApp alert, so each alerts once.
CREATE TABLE dose_alerts (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    medication_id    BIGINT      NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
    dose_date        DATE        NOT NULL,
    scheduled_time   TIME        NOT NULL,
    sent_at          TIMESTAMPTZ NOT NULL,
    UNIQUE (medication_id, dose_date, scheduled_time)
);

CREATE TABLE vitals (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT       NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind             VARCHAR(16)  NOT NULL,
    value1           NUMERIC(6,1) NOT NULL,
    value2           NUMERIC(6,1),
    measured_at      TIMESTAMPTZ  NOT NULL,
    note             VARCHAR(500),
    recorded_by      BIGINT       NOT NULL REFERENCES users(id),
    client_id        VARCHAR(64),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX vitals_org_kind_idx ON vitals (organization_id, kind, measured_at);
CREATE INDEX vitals_org_time_idx ON vitals (organization_id, measured_at);
CREATE UNIQUE INDEX vitals_client_uq ON vitals (organization_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE meals (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    meal_date        DATE        NOT NULL,
    slot             VARCHAR(16) NOT NULL,
    amount           VARCHAR(16),
    glasses          INT         NOT NULL DEFAULT 0,
    note             VARCHAR(500),
    recorded_by      BIGINT      NOT NULL REFERENCES users(id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    client_id        VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX meals_org_date_idx ON meals (organization_id, meal_date);
CREATE UNIQUE INDEX meals_client_uq ON meals (organization_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE journal_entries (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    kind             VARCHAR(16) NOT NULL,
    score            INT,
    body             TEXT,
    photo_file_id    VARCHAR(36) REFERENCES stored_files(id) ON DELETE SET NULL,
    recorded_by      BIGINT      NOT NULL REFERENCES users(id),
    recorded_at      TIMESTAMPTZ NOT NULL,
    client_id        VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX journal_org_time_idx ON journal_entries (organization_id, recorded_at);
CREATE UNIQUE INDEX journal_client_uq ON journal_entries (organization_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE shifts (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES users(id),
    shift_date       DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    kind             VARCHAR(16) NOT NULL,
    note             VARCHAR(300),
    created_by       BIGINT      NOT NULL REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX shifts_org_date_idx ON shifts (organization_id, shift_date);

CREATE TABLE shift_swaps (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    shift_id         BIGINT      NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
    from_user_id     BIGINT      NOT NULL REFERENCES users(id),
    to_user_id       BIGINT      NOT NULL REFERENCES users(id),
    status           VARCHAR(16) NOT NULL,
    message          VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL,
    responded_at     TIMESTAMPTZ
);
CREATE INDEX shift_swaps_org_idx ON shift_swaps (organization_id, status);
CREATE UNIQUE INDEX shift_swaps_one_pending_uq ON shift_swaps (shift_id) WHERE status = 'PENDING';

-- Proof of work: when the carer arrived and left, optionally where.
CREATE TABLE check_ins (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id          BIGINT      NOT NULL REFERENCES users(id),
    checked_in_at    TIMESTAMPTZ NOT NULL,
    checked_out_at   TIMESTAMPTZ,
    in_lat           NUMERIC(9,6),
    in_lng           NUMERIC(9,6),
    in_accuracy      INT,
    out_lat          NUMERIC(9,6),
    out_lng          NUMERIC(9,6),
    out_accuracy     INT,
    client_id        VARCHAR(64),
    out_client_id    VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX check_ins_org_idx ON check_ins (organization_id, checked_in_at);
CREATE UNIQUE INDEX check_ins_open_uq ON check_ins (user_id) WHERE checked_out_at IS NULL;
CREATE UNIQUE INDEX check_ins_client_uq ON check_ins (organization_id, client_id) WHERE client_id IS NOT NULL;

CREATE TABLE supplies (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT       NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name             VARCHAR(120) NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'OK',
    note             VARCHAR(300),
    updated_by       BIGINT       REFERENCES users(id),
    updated_at       TIMESTAMPTZ  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX supplies_org_idx ON supplies (organization_id);

CREATE TABLE supply_events (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT      NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    supply_id        BIGINT      NOT NULL REFERENCES supplies(id) ON DELETE CASCADE,
    status           VARCHAR(16) NOT NULL,
    recorded_by      BIGINT      NOT NULL REFERENCES users(id),
    recorded_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX supply_events_org_idx ON supply_events (organization_id, recorded_at);

CREATE TABLE doctor_visits (
    id                     BIGSERIAL PRIMARY KEY,
    organization_id        BIGINT       NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    visit_date             DATE         NOT NULL,
    doctor_name            VARCHAR(160) NOT NULL,
    specialty              VARCHAR(120),
    place                  VARCHAR(160),
    notes                  TEXT,
    next_date              DATE,
    next_time              TIME,
    prescription_file_id   VARCHAR(36)  REFERENCES stored_files(id) ON DELETE SET NULL,
    recorded_by            BIGINT       NOT NULL REFERENCES users(id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX doctor_visits_org_idx ON doctor_visits (organization_id, visit_date DESC);

CREATE TABLE weekly_summaries (
    id               BIGSERIAL PRIMARY KEY,
    organization_id  BIGINT       NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    week_start       DATE         NOT NULL,
    generated_at     TIMESTAMPTZ  NOT NULL,
    source           VARCHAR(16)  NOT NULL,
    sent_to_user_id  BIGINT       REFERENCES users(id),
    sent_to_name     VARCHAR(160),
    adherence_pct    INT,
    content          TEXT         NOT NULL,
    message_id       BIGINT       REFERENCES outbound_messages(id) ON DELETE SET NULL
);
CREATE INDEX weekly_summaries_org_idx ON weekly_summaries (organization_id, week_start DESC);
