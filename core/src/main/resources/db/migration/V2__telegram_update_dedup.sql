-- Апдейты Telegram, которые ядро уже приняло. Telegram может доставить апдейт повторно,
-- если приёмщик упал до подтверждения offset; повтор отсекается по update_id.
-- Telegram хранит апдейты не дольше 24 часов, поэтому старые записи удаляются по расписанию.
CREATE TABLE telegram_update (
    update_id           BIGINT PRIMARY KEY,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_telegram_update_received_at ON telegram_update(received_at);
