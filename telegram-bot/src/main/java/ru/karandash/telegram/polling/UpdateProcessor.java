package ru.karandash.telegram.polling;

import ru.karandash.telegram.api.Update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Пачка апдейтов: разные чаты обрабатываются параллельно, сообщения одного чата — строго по порядку.
 * Метод возвращается, только когда обработана вся пачка, — после этого можно подтверждать offset.
 */
public class UpdateProcessor {

    private final MessageHandler handler;
    private final ExecutorService executor;

    public UpdateProcessor(MessageHandler handler, ExecutorService executor) {
        this.handler = handler;
        this.executor = executor;
    }

    public void process(List<Update> updates) throws InterruptedException {
        Map<Long, List<Update>> byChat = new LinkedHashMap<>();
        for (Update update : updates) {
            Long chat = update.chatId();
            long chatKey = chat == null ? Long.MIN_VALUE : chat;
            byChat.computeIfAbsent(chatKey, key -> new ArrayList<>()).add(update);
        }
        List<Future<?>> tasks = new ArrayList<>();
        for (List<Update> chatUpdates : byChat.values()) {
            tasks.add(executor.submit(() -> chatUpdates.forEach(handler::handleSafely)));
        }
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (ExecutionException ignored) {
                // handleSafely не бросает; сюда попадают только ошибки самого пула
            }
        }
    }
}
