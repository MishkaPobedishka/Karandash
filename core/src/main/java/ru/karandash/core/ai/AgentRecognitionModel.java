package ru.karandash.core.ai;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.karandash.contracts.ai.AgentLunchRequest;
import ru.karandash.contracts.ai.AgentLunchResponse;
import ru.karandash.contracts.ai.AgentRecognitionResponse;
import ru.karandash.contracts.ai.LunchContract;
import ru.karandash.contracts.ai.AgentTextRequest;
import ru.karandash.contracts.ai.ModelUsage;
import ru.karandash.contracts.ai.RecognitionContract;
import ru.karandash.contracts.ai.RecognitionContractException;
import ru.karandash.contracts.ai.RecognitionResult;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Распознавание через агент-адаптер, который запускает CLI-агента на другой ноде.
 * Ответ агента перепроверяется тем же контрактом: ядро не доверяет чужой ноде на слово.
 */
final class AgentRecognitionModel implements RecognitionModel {

    private final RestClient restClient;

    AgentRecognitionModel(AiProperties.Agent settings, RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(requireSetting(settings == null ? null : settings.baseUrl(), "agent.base-url"))
                .defaultHeaders(headers -> headers.setBearerAuth(
                        requireSetting(settings.serviceToken(), "agent.service-token")))
                .build();
    }

    @Override
    public RecognitionResult recognizeText(String description) {
        return recognizeTextWithUsage(description).result();
    }

    @Override
    public RecognitionResult recognizePhoto(byte[] image, String contentType) {
        return recognizePhotoWithUsage(image, contentType).result();
    }

    @Override
    public ModelRecognition recognizeTextWithUsage(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Описание еды не должно быть пустым");
        }
        return call(() -> restClient.post()
                .uri("/internal/recognition/text")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AgentTextRequest(description))
                .retrieve()
                .body(AgentRecognitionResponse.class));
    }

    @Override
    public ModelRecognition recognizePhotoWithUsage(byte[] image, String contentType) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("Изображение не должно быть пустым");
        }
        HttpHeaders photoHeaders = new HttpHeaders();
        photoHeaders.setContentType(mediaType(contentType));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("photo", new HttpEntity<>(new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "photo";
            }
        }, photoHeaders));
        return call(() -> restClient.post()
                .uri("/internal/recognition/photo")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(AgentRecognitionResponse.class));
    }

    @Override
    public Optional<ModelLunchAdvice> adviseLunch(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }
        try {
            AgentLunchResponse response = restClient.post()
                    .uri("/internal/recognition/lunch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AgentLunchRequest(prompt))
                    .retrieve()
                    .body(AgentLunchResponse.class);
            if (response == null || response.advice() == null) {
                throw new ModelUnavailableException("Агент-адаптер вернул пустой подбор обеда");
            }
            // Ответ чужой ноды перепроверяем своим же контрактом.
            return Optional.of(new ModelLunchAdvice(LunchContract.validate(response.advice()),
                    response.usage() == null ? ModelUsage.unknown() : response.usage()));
        } catch (RecognitionContractException exception) {
            throw new ModelUnavailableException(exception.getMessage(), exception);
        } catch (RestClientResponseException exception) {
            throw new ModelUnavailableException(
                    "Агент-адаптер ответил " + exception.getStatusCode().value(), exception);
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelUnavailableException("Не удалось вызвать агент-адаптер", exception);
        }
    }

    private ModelRecognition call(Supplier<AgentRecognitionResponse> request) {
        try {
            AgentRecognitionResponse response = request.get();
            if (response == null || response.result() == null) {
                throw new ModelUnavailableException("Агент-адаптер вернул пустой ответ");
            }
            return new ModelRecognition(
                    RecognitionContract.validate(response.result()),
                    response.usage() == null ? ModelUsage.unknown() : response.usage());
        } catch (RecognitionContractException exception) {
            throw new ModelUnavailableException(exception.getMessage(), exception);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new IllegalArgumentException("Агент-адаптер отклонил запрос как некорректный", exception);
            }
            throw new ModelUnavailableException(
                    "Агент-адаптер ответил " + exception.getStatusCode().value(), exception);
        } catch (ModelUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ModelUnavailableException("Не удалось вызвать агент-адаптер", exception);
        }
    }

    private static MediaType mediaType(String contentType) {
        try {
            return contentType == null || contentType.isBlank()
                    ? MediaType.IMAGE_JPEG
                    : MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static String requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Не задан параметр karandash.ai." + name);
        }
        return value;
    }
}
