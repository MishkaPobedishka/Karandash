package ru.karandash.telegram.core;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;

/**
 * Передаёт входящее сообщение в ядро (нода РФ) и получает готовый ответ пользователю.
 */
public class CoreClient {

    private final RestClient restClient;

    public CoreClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public TelegramReply submit(TelegramInboundMessage message, byte[] photo) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        HttpHeaders messageHeaders = new HttpHeaders();
        messageHeaders.setContentType(MediaType.APPLICATION_JSON);
        parts.add("message", new HttpEntity<>(message, messageHeaders));
        if (photo != null) {
            HttpHeaders photoHeaders = new HttpHeaders();
            photoHeaders.setContentType(MediaType.IMAGE_JPEG);
            parts.add("photo", new HttpEntity<>(new ByteArrayResource(photo) {
                @Override
                public String getFilename() {
                    return "photo.jpg";
                }
            }, photoHeaders));
        }
        try {
            TelegramReply reply = restClient.post()
                    .uri("/internal/telegram/messages")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts)
                    .retrieve()
                    .body(TelegramReply.class);
            if (reply == null || reply.messages() == null) {
                throw new CoreUnavailableException("Ядро вернуло пустой ответ", null);
            }
            return reply;
        } catch (RestClientResponseException exception) {
            throw new CoreUnavailableException("Ядро ответило " + exception.getStatusCode().value(), exception);
        } catch (RestClientException exception) {
            throw new CoreUnavailableException("Ядро недоступно: " + exception.getClass().getSimpleName(), exception);
        }
    }
}
