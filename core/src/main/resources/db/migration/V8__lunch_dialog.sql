-- Разговор о столовой: пока он открыт, следующие сообщения человека — это уточнение подбора
-- («а полегче», «без мяса», «тогда на 300 ккал»), а не рассказ о съеденном.
CREATE TABLE lunch_dialog (
    account_id          UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    wish                TEXT NOT NULL DEFAULT '',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
