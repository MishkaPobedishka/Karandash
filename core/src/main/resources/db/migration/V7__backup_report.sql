-- Отчёт о резервной копии базы: пишет ночной CronJob, читают метрики и алерт.
-- Хранится в базе, а не в памяти, чтобы перезапуск ядра не выглядел как «бэкапа никогда не было».
CREATE TABLE backup_report (
    name                VARCHAR(32) PRIMARY KEY,
    finished_at         TIMESTAMPTZ NOT NULL,
    last_success_at     TIMESTAMPTZ,
    ok                  BOOLEAN NOT NULL,
    size_bytes          BIGINT NOT NULL DEFAULT 0,
    duration_ms         BIGINT NOT NULL DEFAULT 0,
    message             TEXT
);
