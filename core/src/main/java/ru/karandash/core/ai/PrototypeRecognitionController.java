package ru.karandash.core.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.karandash.contracts.ai.RecognitionResult;

import java.io.IOException;

@RestController
@RequestMapping("/internal/prototype/recognition")
@ConditionalOnProperty(name = "karandash.prototype.enabled", havingValue = "true")
public class PrototypeRecognitionController {

    private static final long MAX_PHOTO_SIZE = 10L * 1024 * 1024;

    private final RecognitionModel recognitionModel;

    public PrototypeRecognitionController(RecognitionModel recognitionModel) {
        this.recognitionModel = recognitionModel;
    }

    @PostMapping(path = "/text", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RecognitionResult recognizeText(@RequestPart String description) {
        return recognitionModel.recognizeText(description);
    }

    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RecognitionResult recognizePhoto(@RequestPart MultipartFile photo) throws IOException {
        if (photo.isEmpty()) {
            throw new IllegalArgumentException("Фотография не должна быть пустой");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE) {
            throw new IllegalArgumentException("Фотография должна быть не больше 10 МБ");
        }
        return recognitionModel.recognizePhoto(photo.getBytes(), photo.getContentType());
    }
}

