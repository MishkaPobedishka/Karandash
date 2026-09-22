package ru.karandash.telegram.testing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Поддельный Telegram Bot API на локальном порту: отдаёт заготовленные апдейты и файлы, запоминает отправленное.
 */
public final class FakeTelegramServer implements AutoCloseable {

    public static final String TOKEN = "123456:TEST-token_secret";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpServer server;
    private final ConcurrentLinkedDeque<String> updateBatches = new ConcurrentLinkedDeque<>();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Map<String, String> errors = new ConcurrentHashMap<>();
    private final Map<String, String> fileIdsToPaths = new ConcurrentHashMap<>();

    public final List<Long> offsets = new CopyOnWriteArrayList<>();
    public final List<JsonNode> sentMessages = new CopyOnWriteArrayList<>();
    public final List<JsonNode> sentPhotos = new CopyOnWriteArrayList<>();
    public final List<JsonNode> sentAlbums = new CopyOnWriteArrayList<>();
    public final List<JsonNode> chatActions = new CopyOnWriteArrayList<>();
    public final List<JsonNode> answeredCallbacks = new CopyOnWriteArrayList<>();
    public final List<JsonNode> editedMarkups = new CopyOnWriteArrayList<>();
    public final List<JsonNode> editedMessages = new CopyOnWriteArrayList<>();
    public final Map<Long, String> chats = new ConcurrentHashMap<>();
    public final List<String> requestPaths = new CopyOnWriteArrayList<>();

    public FakeTelegramServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Следующий ответ getUpdates: JSON-массив апдейтов. */
    public void enqueueUpdates(String updatesJson) {
        updateBatches.add(updatesJson);
    }

    public void addFile(String fileId, String filePath, byte[] bytes) {
        files.put(fileId, bytes);
        files.put("path:" + filePath, bytes);
        fileIdsToPaths.put(fileId, filePath);
    }

    /** Все вызовы метода отвечают ошибкой Bot API. */
    public void failMethod(String method, int errorCode, String description) {
        errors.put(method, "{\"ok\":false,\"error_code\":" + errorCode + ",\"description\":\"" + description + "\"}");
    }

    private void handle(HttpExchange exchange) throws IOException {
        // Сырой путь: настоящий Telegram ждёт токен как есть, процентное кодирование здесь — ошибка клиента.
        String path = exchange.getRequestURI().getRawPath();
        requestPaths.add(path);
        String botPrefix = "/bot" + TOKEN + "/";
        String filePrefix = "/file/bot" + TOKEN + "/";
        if (path.startsWith(filePrefix)) {
            byte[] bytes = files.get("path:" + path.substring(filePrefix.length()));
            if (bytes == null) {
                respond(exchange, 404, "{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}");
            } else {
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(bytes);
                }
            }
            return;
        }
        if (!path.startsWith(botPrefix)) {
            respond(exchange, 401, "{\"ok\":false,\"error_code\":401,\"description\":\"Unauthorized\"}");
            return;
        }
        String method = path.substring(botPrefix.length());
        JsonNode request = objectMapper.readTree(exchange.getRequestBody().readAllBytes());
        String error = errors.get(method);
        if (error != null) {
            respond(exchange, objectMapper.readTree(error).path("error_code").asInt(), error);
            return;
        }
        switch (method) {
            case "getUpdates" -> {
                offsets.add(request.path("offset").asLong());
                String batch = updateBatches.poll();
                if (batch == null) {
                    sleep();
                    batch = "[]";
                }
                respond(exchange, 200, "{\"ok\":true,\"result\":" + batch + "}");
            }
            case "getFile" -> {
                String fileId = request.path("file_id").asText();
                String filePath = fileIdsToPaths.get(fileId);
                byte[] bytes = files.get(fileId);
                if (filePath == null || bytes == null) {
                    respond(exchange, 400, "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: file not found\"}");
                } else {
                    respond(exchange, 200, "{\"ok\":true,\"result\":{\"file_id\":\"" + fileId + "\",\"file_size\":"
                            + bytes.length + ",\"file_path\":\"" + filePath + "\"}}");
                }
            }
            case "sendMessage" -> {
                sentMessages.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":" + sentMessages.size() + "}}");
            }
            case "sendPhoto" -> {
                sentPhotos.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
            }
            case "sendMediaGroup" -> {
                sentAlbums.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":[{\"message_id\":1}]}");
            }
            case "answerCallbackQuery" -> {
                answeredCallbacks.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":true}");
            }
            case "editMessageText" -> {
                editedMessages.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
            }
            case "getChat" -> {
                String chat = chats.get(request.path("chat_id").asLong());
                if (chat == null) {
                    respond(exchange, 400, "{\"ok\":false,\"error_code\":400,\"description\":\"Bad Request: chat not found\"}");
                } else {
                    respond(exchange, 200, "{\"ok\":true,\"result\":" + chat + "}");
                }
            }
            case "editMessageReplyMarkup" -> {
                editedMarkups.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":{\"message_id\":1}}");
            }
            case "sendChatAction" -> {
                chatActions.add(request);
                respond(exchange, 200, "{\"ok\":true,\"result\":true}");
            }
            default -> respond(exchange, 404, "{\"ok\":false,\"error_code\":404,\"description\":\"Not Found\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
