-- Серия дней: сколько дней подряд человек записывает еду. Пропуск до трёх дней подряд серию
-- не рвёт, четвёртый — обнуляет. Считается по дням в часовом поясе человека.
CREATE TABLE streak (
    account_id          UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    current_days        INTEGER NOT NULL DEFAULT 0,
    best_days           INTEGER NOT NULL DEFAULT 0,
    last_active_on      DATE,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
