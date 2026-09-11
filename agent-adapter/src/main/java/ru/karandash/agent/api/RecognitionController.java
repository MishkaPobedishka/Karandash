package ru.karandash.agent.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.karandash.agent.recognition.RecognitionService;
import ru.karandash.contracts.ai.AgentRecognitionResponse;
import ru.karandash.contracts.ai.AgentTextRequest;

import java.io.IOException;

@RestController
@RequestMapping("/internal/recognition")
public class RecognitionController {

    private final RecognitionService recognitionService;

    public RecognitionController(RecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    @PostMapping(path = "/text", consumes = MediaType.APPLICATION_JSON_VALUE)
    AgentRecognitionResponse recognizeText(@RequestBody AgentTextRequest request) {
        return recognitionService.recognizeText(request == null ? null : request.description());
    }

    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AgentRecognitionResponse recognizePhoto(@RequestPart("photo") MultipartFile photo) throws IOException {
        return recognitionService.recognizePhoto(photo.getBytes());
    }
}
