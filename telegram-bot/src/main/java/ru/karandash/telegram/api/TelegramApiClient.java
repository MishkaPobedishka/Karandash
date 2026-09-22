package ru.karandash.telegram.api;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.karandash.contracts.telegram.ReplyButton;
import ru.karandash.contracts.telegram.ReplyPhoto;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Тонкий клиент Telegram Bot API: только методы, которые нужны приёмщику.
 * Токен бота стоит в пути каждого запроса, поэтому исходные исключения HTTP-клиента наружу не выпускаются.
 */
public class TelegramApiClient {

    public static final int MESSAGE_LIMIT = 4096;
    private static final Pattern FILE_PATH = Pattern.compile("[A-Za-z0-9_\\-][A-Za-z0-9_\\-./]*");

    private final RestClient restClient;
    private final String token;
    private final ObjectMapper objectMapper;

    public TelegramApiClient(RestClient restClient, String token, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.token = token;
        this.objectMapper = objectMapper;
    }

    public List<Update> getUpdates(long offset, Duration timeout) {
        return call("getUpdates", Map.of(
                "offset", offset,
                "timeout", timeout.toSeconds(),
                "allowed_updates", List.of("message", "callback_query")
        ), objectMapper.getTypeFactory().constructCollectionType(List.class, Update.class));
    }

    /** Кто это: имя и «собачка» человека, который писал боту. */
    public Chat getChat(long chatId) {
        return call("getChat", Map.of("chat_id", chatId), objectMapper.constructType(Chat.class));
    }

    public TelegramFile getFile(String fileId) {
        return call("getFile", Map.of("file_id", fileId), objectMapper.constructType(TelegramFile.class));
    }

    /** Скачивает файл в память, обрывая скачивание сверх лимита. */
    public byte[] downloadFile(String filePath, long maxBytes) {
        if (filePath == null || !FILE_PATH.matcher(filePath).matches() || filePath.contains("..")) {
            throw new TelegramApiException("Telegram вернул недопустимый путь файла", null, null);
        }
        try {
            return restClient.get()
                    .uri(builder -> builder.path("/file/bot" + token + "/").path(filePath).build())
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new TelegramApiException("Не удалось скачать файл из Telegram",
                                    response.getStatusCode().value(), null);
                        }
                        try (InputStream body = response.getBody()) {
                            byte[] bytes = body.readNBytes(Math.toIntExact(Math.min(maxBytes + 1, Integer.MAX_VALUE)));
                            if (bytes.length > maxBytes) {
                                throw new FileTooLargeException();
                            }
                            return bytes;
                        }
                    });
        } catch (TelegramApiException | FileTooLargeException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw networkError("download", exception);
        }
    }

    public void sendMessage(long chatId, String text) {
        sendMessage(chatId, text, List.of());
    }

    /** Кнопки идут одна под другой: подписи длинные, в ряд на телефоне они не помещаются. */
    public void sendMessage(long chatId, String text, List<ReplyButton> buttons) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "chat_id", chatId,
                "text", text.length() <= MESSAGE_LIMIT ? text : text.substring(0, MESSAGE_LIMIT),
                "link_preview_options", Map.of("is_disabled", true)
        ));
        if (buttons != null && !buttons.isEmpty()) {
            body.put("reply_markup", Map.of("inline_keyboard", keyboard(buttons)));
        }
        call("sendMessage", body, objectMapper.constructType(Object.class));
    }

    /**
     * Фотографии по ссылкам: несколько — одним альбомом, одна — обычным фото.
     * Альбом кнопок не держит, поэтому он уходит отдельно от текста с кнопками.
     */
    public void sendPhotos(long chatId, List<ReplyPhoto> photos) {
        if (photos == null || photos.isEmpty()) {
            return;
        }
        List<ReplyPhoto> album = photos.size() <= ReplyPhoto.MAX_IN_ALBUM
                ? photos
                : photos.subList(0, ReplyPhoto.MAX_IN_ALBUM);
        if (album.size() == 1) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("chat_id", chatId);
            body.put("photo", album.getFirst().url());
            putCaption(body, album.getFirst());
            call("sendPhoto", body, objectMapper.constructType(Object.class));
            return;
        }
        List<Map<String, Object>> media = album.stream().map(photo -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "photo");
            item.put("media", photo.url());
            putCaption(item, photo);
            return item;
        }).toList();
        call("sendMediaGroup", Map.of("chat_id", chatId, "media", media),
                objectMapper.constructType(Object.class));
    }

    private static void putCaption(Map<String, Object> body, ReplyPhoto photo) {
        String caption = photo.caption();
        if (caption == null || caption.isBlank()) {
            return;
        }
        body.put("caption", caption.length() <= ReplyPhoto.MAX_CAPTION
                ? caption
                : caption.substring(0, ReplyPhoto.MAX_CAPTION));
    }

    /** Ответ на нажатие: без него Telegram крутит часы на кнопке до таймаута. */
    public void answerCallbackQuery(String callbackQueryId) {
        call("answerCallbackQuery", Map.of("callback_query_id", callbackQueryId),
                objectMapper.constructType(Boolean.class));
    }

    /**
     * Переписывает сообщение вместе с кнопками — так меню переключается на месте, а не плодит сообщения.
     *
     * @return {@code false}, если Telegram отказался и придётся отправить обычное сообщение
     */
    public boolean editMessage(long chatId, long messageId, String text, List<ReplyButton> buttons) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "chat_id", chatId,
                "message_id", messageId,
                "text", text.length() <= MESSAGE_LIMIT ? text : text.substring(0, MESSAGE_LIMIT),
                "link_preview_options", Map.of("is_disabled", true),
                "reply_markup", Map.of("inline_keyboard", keyboard(buttons))));
        try {
            call("editMessageText", body, objectMapper.constructType(Object.class));
            return true;
        } catch (TelegramApiException exception) {
            // «message is not modified» — экран уже такой, это не ошибка.
            return exception.getMessage() != null && exception.getMessage().contains("not modified");
        }
    }

    /** Снимает кнопки у сообщения, чтобы на ту же оценку нельзя было нажать второй раз. */
    public void clearButtons(long chatId, long messageId) {
        call("editMessageReplyMarkup", Map.of(
                "chat_id", chatId,
                "message_id", messageId,
                "reply_markup", Map.of("inline_keyboard", List.of())
        ), objectMapper.constructType(Object.class));
    }

    public void sendChatAction(long chatId, String action) {
        call("sendChatAction", Map.of("chat_id", chatId, "action", action), objectMapper.constructType(Boolean.class));
    }

    /**
     * По кнопке на строку — подписи длинные и в ряд на телефоне не помещаются. Исключение: кнопки,
     * которым ядро проставило один и тот же номер ряда, например «−1 ч / −10 мин / +10 мин / +1 ч».
     */
    private static List<List<Map<String, String>>> keyboard(List<ReplyButton> buttons) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        int openRow = ReplyButton.OWN_ROW;
        for (ReplyButton button : buttons == null ? List.<ReplyButton>of() : buttons) {
            Map<String, String> item = Map.of("text", button.text(), "callback_data", button.data());
            if (button.row() != ReplyButton.OWN_ROW && button.row() == openRow && !rows.isEmpty()) {
                rows.getLast().add(item);
                continue;
            }
            rows.add(new ArrayList<>(List.of(item)));
            openRow = button.row();
        }
        return rows;
    }

    private <T> T call(String method, Object body, JavaType resultType) {
        ApiResponse<T> response;
        try {
            response = restClient.post()
                    // Токен — литералом: как переменная шаблона он кодируется строго, и «:» уходит как %3A.
                    .uri(builder -> builder.path("/bot" + token + "/" + method).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, httpResponse) -> {
                        try (InputStream stream = httpResponse.getBody()) {
                            return objectMapper.readValue(stream,
                                    objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, resultType));
                        } catch (IOException exception) {
                            throw new TelegramApiException("Telegram вернул нечитаемый ответ на " + method,
                                    httpResponse.getStatusCode().value(), null);
                        }
                    });
        } catch (TelegramApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw networkError(method, exception);
        }
        if (response == null || !response.ok()) {
            Integer retryAfter = response == null || response.parameters() == null
                    ? null
                    : response.parameters().retryAfter();
            throw new TelegramApiException(
                    "Telegram отклонил " + method + ": " + (response == null ? "пустой ответ" : response.description()),
                    response == null ? null : response.errorCode(), retryAfter);
        }
        return response.result();
    }

    private static TelegramApiException networkError(String method, RuntimeException exception) {
        // Сообщение исходного исключения содержит URL с токеном — берём только тип ошибки.
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return new TelegramApiException("Сетевая ошибка при вызове " + method + ": " + root.getClass().getSimpleName(),
                null, null);
    }

    public static class FileTooLargeException extends RuntimeException {

        public FileTooLargeException() {
            super("Файл из Telegram больше допустимого размера");
        }
    }
}
