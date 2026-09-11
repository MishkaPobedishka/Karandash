CREATE TABLE account (
    id                  UUID PRIMARY KEY,
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE telegram_identity (
    telegram_id         BIGINT PRIMARY KEY,
    account_id          UUID NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE profile (
    account_id          UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    weight_kg           NUMERIC(6,2) NOT NULL,
    height_cm           NUMERIC(6,2) NOT NULL,
    age                 INTEGER NOT NULL,
    sex                 VARCHAR(16) NOT NULL,
    activity_level      NUMERIC(4,2) NOT NULL,
    restrictions_text  TEXT,
    meal_pattern        TEXT,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE goal (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    type                VARCHAR(24) NOT NULL,
    start_weight_kg     NUMERIC(6,2),
    target_weight_kg    NUMERIC(6,2),
    deadline            DATE,
    kcal_min            INTEGER NOT NULL,
    kcal_max            INTEGER NOT NULL,
    protein_g_min       NUMERIC(7,2) NOT NULL,
    protein_g_max       NUMERIC(7,2) NOT NULL,
    fat_g_min           NUMERIC(7,2) NOT NULL,
    fat_g_max           NUMERIC(7,2) NOT NULL,
    carbs_g_min         NUMERIC(7,2) NOT NULL,
    carbs_g_max         NUMERIC(7,2) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_goal_active ON goal(account_id) WHERE status = 'ACTIVE';

CREATE TABLE meal (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    local_date          DATE NOT NULL,
    local_time          TIME NOT NULL,
    meal_type           VARCHAR(32),
    input_source        VARCHAR(16) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    telegram_file_id    TEXT,
    input_text          TEXT,
    kcal_min            INTEGER,
    kcal_max            INTEGER,
    kcal_likely         INTEGER,
    kcal_confirmed      INTEGER,
    protein_g_min       NUMERIC(7,2),
    protein_g_max       NUMERIC(7,2),
    fat_g_min           NUMERIC(7,2),
    fat_g_max           NUMERIC(7,2),
    carbs_g_min         NUMERIC(7,2),
    carbs_g_max         NUMERIC(7,2),
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_meal_account_day ON meal(account_id, local_date);

CREATE TABLE food_item (
    id                  UUID PRIMARY KEY,
    meal_id             UUID NOT NULL REFERENCES meal(id) ON DELETE CASCADE,
    name                TEXT NOT NULL,
    amount_min          NUMERIC(9,2),
    amount_max          NUMERIC(9,2),
    unit                VARCHAR(24),
    kcal_min            INTEGER,
    kcal_max            INTEGER,
    protein_g_min       NUMERIC(7,2),
    protein_g_max       NUMERIC(7,2),
    fat_g_min           NUMERIC(7,2),
    fat_g_max           NUMERIC(7,2),
    carbs_g_min         NUMERIC(7,2),
    carbs_g_max         NUMERIC(7,2),
    estimate_source     VARCHAR(32) NOT NULL,
    confidence          NUMERIC(4,3),
    user_corrections    JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE daily_summary (
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    local_date          DATE NOT NULL,
    goal_id             UUID NOT NULL REFERENCES goal(id),
    kcal_min            INTEGER NOT NULL DEFAULT 0,
    kcal_max            INTEGER NOT NULL DEFAULT 0,
    kcal_likely         INTEGER NOT NULL DEFAULT 0,
    meals_count         INTEGER NOT NULL DEFAULT 0,
    budget_state        VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, local_date)
);

CREATE TABLE preference (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    type                VARCHAR(64) NOT NULL,
    value               TEXT NOT NULL,
    source              VARCHAR(32) NOT NULL,
    confirmed           BOOLEAN NOT NULL DEFAULT false,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reminder_setting (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    type                VARCHAR(32) NOT NULL,
    local_time          TIME NOT NULL,
    weekdays            SMALLINT[] NOT NULL DEFAULT ARRAY[1,2,3,4,5,6,7],
    quiet_from          TIME,
    quiet_to            TIME,
    enabled             BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE external_menu (
    id                  UUID PRIMARY KEY,
    source              VARCHAR(32) NOT NULL,
    zone                VARCHAR(128) NOT NULL,
    fetched_at          TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ,
    UNIQUE (source, zone, fetched_at)
);

CREATE TABLE menu_item (
    id                  UUID PRIMARY KEY,
    menu_id             UUID NOT NULL REFERENCES external_menu(id) ON DELETE CASCADE,
    external_id         TEXT NOT NULL,
    name                TEXT NOT NULL,
    portion             TEXT,
    kcal_min            INTEGER,
    kcal_max            INTEGER,
    protein_g           NUMERIC(7,2),
    fat_g               NUMERIC(7,2),
    carbs_g             NUMERIC(7,2),
    price               NUMERIC(12,2),
    url                 TEXT,
    available           BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (menu_id, external_id)
);

CREATE TABLE plan (
    id                  UUID PRIMARY KEY,
    code                VARCHAR(64) NOT NULL UNIQUE,
    name                TEXT NOT NULL,
    limits              JSONB NOT NULL,
    price               NUMERIC(12,2) NOT NULL DEFAULT 0,
    active              BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE subscription (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    plan_id             UUID NOT NULL REFERENCES plan(id),
    status              VARCHAR(24) NOT NULL,
    period_start        TIMESTAMPTZ NOT NULL,
    period_end          TIMESTAMPTZ,
    source              VARCHAR(24) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_counter (
    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    period              DATE NOT NULL,
    photo_used          INTEGER NOT NULL DEFAULT 0,
    text_used           INTEGER NOT NULL DEFAULT 0,
    recommendations_used INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, period)
);

CREATE TABLE usage_record (
    id                  UUID PRIMARY KEY,
    account_id          UUID REFERENCES account(id) ON DELETE SET NULL,
    kind                VARCHAR(32) NOT NULL,
    provider            VARCHAR(64) NOT NULL,
    model               VARCHAR(128) NOT NULL,
    tokens_in           INTEGER,
    tokens_out          INTEGER,
    cost                NUMERIC(16,6),
    latency_ms          BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id                  UUID PRIMARY KEY,
    admin_id            VARCHAR(128) NOT NULL,
    action              VARCHAR(128) NOT NULL,
    account_id          UUID REFERENCES account(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_event (
    id                  UUID PRIMARY KEY,
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        UUID NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    payload             JSONB NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    attempts            INTEGER NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error          TEXT
);
CREATE INDEX ix_outbox_pending ON outbox_event(next_attempt_at, created_at)
    WHERE published_at IS NULL;

INSERT INTO plan (id, code, name, limits, price, active)
VALUES ('00000000-0000-0000-0000-000000000001', 'pilot', 'Внутренний пилот',
        '{"photo_per_month": null, "text_per_month": null, "recs_per_day": null}'::jsonb,
        0, true);

