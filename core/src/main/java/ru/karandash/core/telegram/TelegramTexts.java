package ru.karandash.core.telegram;

/**
 * Тексты ответов пользователю. Отправляются простым текстом, без разметки.
 */
final class TelegramTexts {

    static final String GREETING = """
            Привет! Я Карандаш — помогаю оценить, сколько калорий в еде.

            Пришлите фото тарелки или опишите блюдо словами, например: «гречка с курицей, обычная тарелка». \
            Верну состав с диапазонами калорий и БЖУ и спрошу, записать ли это в дневник. \
            Если оценка мимо — просто напишите, что не так, и я пересчитаю.

            /profile — рост, вес, возраст и цель: посчитаю вашу норму калорий.
            /diary — что уже съедено сегодня.""";

    static final String HELP = """
            Пришлите фото еды или опишите её словами — верну оценку калорий и БЖУ диапазонами \
            и кнопки «Записать в дневник» и «Не записывать».

            Пока оценка не закрыта, ваше сообщение я понимаю как уточнение к ней и считаю заново. \
            Чтобы начать с другой еды — нажмите «Не записывать» или пришлите новое фото.

            /profile — телосложение и цель, считаю норму калорий на день.
            /diary — записи за сегодня и сколько осталось до нормы.
            /lunch — что взять на обед в столовой сегодня.
            /changelog — что нового в боте.
            /id — ваш номер в Telegram: он нужен администратору, чтобы открыть доступ.""";

    static final String UNKNOWN_COMMAND = "Такой команды нет. Пришлите фото еды или опишите её словами.";

    static final String TOO_LONG = "Слишком длинное описание. Опишите блюдо короче — до %d символов.";

    static final String CANNOT_READ_INPUT =
            "Не получилось прочитать сообщение. Пришлите фото в JPEG или PNG или опишите еду словами.";

    static final String MODEL_UNAVAILABLE = "Сейчас не получается распознать еду. Попробуйте ещё раз через пару минут.";

    static final String NOTHING_RECOGNIZED =
            "Не получилось узнать еду. Опишите блюдо словами или пришлите другое фото.";

    /** Подпись под оценкой, которую можно записать. */
    static final String SAVE_QUESTION = """
            Записать в дневник? Если что-то не так — напишите, что именно, и я пересчитаю.""";

    /** Подпись под оценкой, для которой модель попросила уточнений. */
    static final String ANSWER_OR_AS_IS = """
            Ответьте сообщением — или нажмите «Оцени как есть», и я прикину по типичному варианту.""";

    static final String BUTTON_SAVE = "✓ Записать в дневник";

    static final String BUTTON_DROP = "✗ Не записывать";

    static final String BUTTON_AS_IS = "Оцени как есть";

    static final String BUTTON_CANCEL = "Отменить";

    static final String BUTTON_PROFILE_FILL = "Посчитать норму";

    static final String BUTTON_PROFILE_CHANGE = "Пересчитать";

    static final String BUTTON_ACCESS_GRANT = "✓ Выдать доступ";

    static final String BUTTON_ACCESS_DENY = "✗ Отказать";

    static final String BUTTON_CHANGELOG_SEND = "Разослать всем";

    static final String BUTTON_CHANGELOG_DROP = "✗ Удалить";

    static final String DROPPED = "Не записал. Пришлите фото или опишите еду — оценю заново.";

    static final String DRAFT_GONE = "Эта оценка уже закрыта. Пришлите фото или опишите еду — оценю заново.";

    static final String NOTHING_TO_SAVE = "Записывать пока нечего: сначала нужна оценка с блюдами.";

    static final String DIARY_EMPTY = "Сегодня в дневнике пусто. Пришлите фото еды или опишите её словами.";

    // Доступ

    static final String ACCESS_BETA = """
            Карандаш пока в закрытом бета-тесте: доступ открывает администратор.

            Нажмите «Подать заявку» — я передам её администраторам и напишу, когда решат.
            Ваш номер: %d.""";

    static final String ACCESS_REQUESTED = "Заявка отправлена администраторам. Напишу, как только её рассмотрят.";

    static final String ACCESS_WAITING = "Заявка уже отправлена — ждём ответа администратора.";

    static final String ACCESS_BLOCKED = "Доступ к боту закрыт.";

    static final String BUTTON_REQUEST_ACCESS = "Подать заявку";

    static final String ACCESS_GRANTED_BY = """
            Доступ открыл %s.

            Пришлите фото еды или опишите блюдо словами — верну оценку калорий.
            Заодно посчитаю вашу норму на день: /profile.""";

    static final String ACCESS_DENIED = "Заявку отклонили. Доступ к боту не открыт.";

    static final String ACCESS_REQUEST = "Новая заявка на доступ: %s, номер %d.";

    static final String ACCESS_DECIDED_ALLOWED = "Доступ открыт: %s.";

    static final String ACCESS_DECIDED_BLOCKED = "Заявка отклонена: %s.";

    static final String ACCESS_ALREADY_DECIDED = "Заявку уже закрыл %s.";

    static final String ACCESS_DECISION_INFO = "Заявку от %s %s %s.";

    static final String ACCESS_MY_NUMBER = "Ваш номер в Telegram: %d.";

    static final String ACCESS_UNKNOWN_USER = "Такого номера нет. Пользователь должен сначала написать боту.";

    static final String ACCESS_GRANTED_ADMIN = "Доступ выдан: %s. Пользователю написал.";

    static final String ACCESS_DENIED_ADMIN = "Доступ закрыт: %s.";

    static final String ACCESS_PROMOTED_ADMIN = "Теперь администратор: %s.";

    static final String ADMIN_ONLY = "Эта команда только для администратора.";

    static final String ADMIN_HELP = """
            Панель администратора — кнопки ниже. То же самое командами:

            /users — кто ждёт доступа и у кого он есть
            /grant НОМЕР — выдать доступ
            /revoke НОМЕР — закрыть доступ
            /promote НОМЕР — сделать администратором
            /changelog ТЕКСТ — написать «что нового»: первая строка — заголовок, дальше текст. Рассылка — кнопкой.""";

    static final String ADMIN_NEED_NUMBER = "Нужен номер пользователя: %s 123456789";

    static final String ADMIN_HINT = """


            /admin — панель администратора.""";

    static final String ADMIN_PANEL = "Панель администратора. Заявок: %d, с доступом: %d.";

    static final String ADMIN_NO_REQUESTS = "Заявок нет.";

    static final String ADMIN_REQUESTS = "Ждут решения:";

    static final String ADMIN_USERS = "Кому открыт бот — нажмите на человека, чтобы решить его судьбу:";

    static final String ADMIN_USER_CARD = """
            %s
            Номер: %d
            Доступ: %s
            Роль: %s""";

    static final String ADMIN_USER_GONE = "Этого пользователя больше нет.";

    static final String BUTTON_ADMIN_REQUESTS = "Заявки (%d)";

    static final String BUTTON_ADMIN_USERS = "Пользователи (%d)";

    static final String BUTTON_ADMIN_CHANGELOG = "Что нового";

    static final String BUTTON_ADMIN_BACK = "← Назад";

    static final String BUTTON_ACCESS_REVOKE = "✗ Забрать доступ";

    static final String BUTTON_ACCESS_PROMOTE = "★ Сделать администратором";

    static final String BUTTON_ACCESS_ALLOW = "✓ Открыть доступ";

    // Телосложение

    static final String PROFILE_NOT_SET = """
            Нормы пока нет. Посчитаю по формуле Миффлина — Сан Жеора: спрошу пол, возраст, рост, вес, \
            сколько двигаетесь и какая цель.""";

    static final String PROFILE_BAD_ANSWER = "Не понял ответ.";

    static final String PROFILE_CANCELLED = "Остановились. Вернуться к расчёту — /profile.";

    static final String PROFILE_BUSY = "Сначала закончим с нормой калорий — ответьте на вопрос выше или нажмите «Отменить».";

    // Столовая

    static final String LUNCH_MAILING_TITLE = "Что сегодня взять на обед в столовой:";

    static final String LUNCH_ANSWER_TITLE = "Сегодня в столовой:";

    static final String LUNCH_FOOTER = "Съели — пришлите фото или опишите словами, запишу в дневник.";

    static final String LUNCH_MENU_TITLE = "Всё меню столовой на сегодня:";

    static final String BUTTON_LUNCH_MENU = "🍽 Меню столовой";

    static final String BUTTON_LUNCH_FULL = "Показать всё меню";

    static final String LUNCH_UNAVAILABLE =
            "Меню столовой сейчас недоступно. Попробуйте позже или опишите еду словами.";

    static final String LUNCH_MAILING_ON = "Буду присылать подбор обеда по будням в 10:00.";

    static final String LUNCH_MAILING_OFF = "Больше не присылаю подбор обеда. Вернуть — команда /lunch.";

    static final String BUTTON_LUNCH_OFF = "Не присылать по утрам";

    static final String BUTTON_LUNCH_ON = "Присылать по утрам";

    // Что нового

    static final String CHANGELOG_EMPTY = "Пока ничего не публиковали.";

    static final String CHANGELOG_DRAFT = "Черновик. Разослать всем, у кого есть доступ?";

    static final String CHANGELOG_SENT = "Разослал. Получателей: %d.";

    static final String CHANGELOG_DELETED = "Черновик удалён.";

    static final String CHANGELOG_GONE = "Этой записи уже нет.";

    static final String CHANGELOG_ALREADY_SENT = "Эту запись уже рассылали.";

    private TelegramTexts() {
    }
}
