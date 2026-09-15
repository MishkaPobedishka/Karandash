-- Черновик оценки: живёт между ответом модели и решением пользователя — записать в дневник или нет.
-- Один активный черновик на аккаунт: новая оценка вытесняет прошлую.
CREATE TABLE meal_draft (
    id                  UUID PRIMARY KEY,
    account_id          UUID NOT NULL UNIQUE REFERENCES account(id) ON DELETE CASCADE,
    input_source        VARCHAR(16) NOT NULL,
    input_text          TEXT,
    result_json         TEXT NOT NULL,
    revision            INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
