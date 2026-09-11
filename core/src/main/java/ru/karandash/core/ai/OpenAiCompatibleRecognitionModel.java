package ru.karandash.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import ru.karandash.contracts.ai.RecognitionResult;

import java.util.Base64;
import java.util.List;
import java.util.Map;

final class OpenAiCompatibleRecognitionModel implements RecognitionModel {

    private static final String SYSTEM_PROMPT = """
            Ты оцениваешь состав обычной еды для личного дневника питания.
            Верни только JSON с массивами items и questions. Для каждой позиции укажи:
            name, portion_g_min, portion_g_max, kcal_min, kcal_max,
            protein_g_min, protein_g_max, fat_g_min, fat_g_max,
            carbs_g_min, carbs_g_max, confidence от 0 до 1.
            Не создавай ложную точность. Если оценка невозможна, не выдумывай числа,
            оставь items пустым и задай не более двух коротких уточняющих вопросов.
            """;

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
                Map.of("type", "text", "text", "Распознай блюда, ингредиенты и диапазоны порций на фотографии."),
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
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
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
            return validate(objectMapper.readValue(stripCodeFence(content), RecognitionResult.class));
        } catch (JsonProcessingException exception) {
            throw new ModelUnavailableException("Слой модели вернул некорректный JSON", exception);
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelUnavailableException("Не удалось вызвать слой модели", exception);
        }
    }

    private RecognitionResult validate(RecognitionResult result) {
        if (result.items() == null || result.questions() == null || result.questions().size() > 2) {
            throw new ModelUnavailableException("Результат распознавания не соответствует контракту");
        }
        result.items().forEach(item -> {
            if (item.name() == null || item.name().isBlank()
                    || item.kcalMin() == null || item.kcalMax() == null
                    || item.kcalMin() < 0 || item.kcalMax() < item.kcalMin()
                    || item.confidence() == null
                    || item.confidence().signum() < 0
                    || item.confidence().compareTo(java.math.BigDecimal.ONE) > 0) {
                throw new ModelUnavailableException("Позиция распознавания не соответствует контракту");
            }
        });
        return result;
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstLineEnd >= 0 && lastFence > firstLineEnd
                ? trimmed.substring(firstLineEnd + 1, lastFence).trim()
                : trimmed;
    }

    private String requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр karandash.ai." + name);
        }
        return value;
    }
}

