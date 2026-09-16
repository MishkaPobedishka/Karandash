-- Бот в бета-тесте: доступ выдаётся по заявке, решение принимает любой администратор — чьё пришло первым.
ALTER TABLE account ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE account ADD COLUMN access VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE account ADD COLUMN access_changed_at TIMESTAMPTZ;
-- Кто решил судьбу заявки: видно и пользователю, и остальным администраторам.
ALTER TABLE account ADD COLUMN decided_by UUID REFERENCES account(id) ON DELETE SET NULL;

-- Те, кто уже пользовался ботом, доступ не теряют.
UPDATE account SET access = 'ALLOWED', access_changed_at = now();

-- Имя из Telegram: администратор должен видеть, кого пускает, а пользователь — кто его пустил.
ALTER TABLE telegram_identity ADD COLUMN display_name TEXT;
ALTER TABLE telegram_identity ADD COLUMN username TEXT;

-- Незаконченный ввод телосложения: шаг разговора и уже названные значения.
CREATE TABLE profile_setup (
    account_id          UUID PRIMARY KEY REFERENCES account(id) ON DELETE CASCADE,
    step                VARCHAR(24) NOT NULL,
    data                TEXT NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Что нового: пишет администратор, он же рассылает.
CREATE TABLE changelog_entry (
    id                  UUID PRIMARY KEY,
    title               TEXT NOT NULL,
    body                TEXT NOT NULL,
    created_by          UUID REFERENCES account(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    recipients          INTEGER
);
CREATE INDEX ix_changelog_published ON changelog_entry(published_at DESC);

-- Первая запись про уже сделанное: лежит черновиком, рассылает её администратор.
INSERT INTO changelog_entry (id, title, body) VALUES (
    '00000000-0000-0000-0000-000000000001',
    'Дневник и разговор с уточнениями',
    'Что нового:

• Оценка приходит с кнопками — «Записать в дневник» или «Не записывать».
• Не согласны с оценкой? Напишите, что не так, и я пересчитаю ту же еду.
• Если уточнить нечем, есть кнопка «Оцени как есть».
• Записи копятся в личном дневнике: команда /diary.
• Команда /profile — рост, вес, возраст, активность и цель. Посчитаю вашу норму калорий и покажу, сколько осталось на сегодня.'
);
