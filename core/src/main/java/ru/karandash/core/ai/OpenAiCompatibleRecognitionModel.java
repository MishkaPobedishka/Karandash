package ru.karandash.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.karandash.contracts.ai.RecognitionContract;
import ru.karandash.contracts.ai.RecognitionContractException;
import ru.karandash.contracts.ai.RecognitionResult;

import java.util.Base64;
import java.util.List;
import java.util.Map;

final class OpenAiCompatibleRecognitionModel implements RecognitionModel {

    private final AiProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    OpenAiCompatibleRecognitionModel(
            AiProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(requireSetting(properties.baseUrl(), "base-url"))
                .defaultHeader("Authorization", "Bearer " + requireSetting(properties.apiKey(), "api-key"))
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public RecognitionResult recognizeText(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Описание еды не должно быть пустым");
        }
        return call(requireSetting(properties.textModel(), "text-model"), description);
    }

    @Override
    public RecognitionResult recognizePhoto(byte[] image, String contentType) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Изображение не должно быть пустым");
        }
        String safeContentType = contentType == null || contentType.isBlank() ? "image/jpeg" : contentType;
        String dataUrl = "data:" + safeContentType + ";base64," + Base64.getEncoder().encodeToString(image);
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", RecognitionContract.PHOTO_REQUEST),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
        );
        return call(requireSetting(properties.visionModel(), "vision-model"), content);
    }

    private RecognitionResult call(String model, Object userContent) {
        Map<String, Object> request = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", RecognitionContract.MODEL_INSTRUCTIONS),
                        Map.of("role", "user", "content", userContent)
                )
        );

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw new ModelUnavailableException("Слой модели вернул пустой ответ");
            }
            String content = response.at("/choices/0/message/content").asText();
            if (content.isBlank()) {
                throw new ModelUnavailableException("В ответе слоя модели нет результата распознавания");
            }
            return RecognitionContract.validate(
                    objectMapper.readValue(RecognitionContract.stripCodeFence(content), RecognitionResult.class));
        } catch (JsonProcessingException exception) {
            throw new ModelUnavailableException("Слой модели вернул некорректный JSON", exception);
        } catch (RecognitionContractException exception) {
            throw new ModelUnavailableException(exception.getMessage(), exception);
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelUnavailableException("Не удалось вызвать слой модели", exception);
        }
    }

    private String requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр karandash.ai." + name);
        }
        return value;
    }
}
