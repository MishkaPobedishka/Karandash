-- Напоминания про еду. Таблица reminder_setting была в каркасе, но не использовалась:
-- добавляем к ней отметку об отправке за день и запрет на два напоминания одного вида у человека.
ALTER TABLE reminder_setting ADD COLUMN last_sent_on DATE;
ALTER TABLE reminder_setting ADD CONSTRAINT reminder_setting_account_type_key UNIQUE (account_id, type);
