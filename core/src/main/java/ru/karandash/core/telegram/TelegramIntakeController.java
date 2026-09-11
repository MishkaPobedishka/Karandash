package ru.karandash.core.telegram;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.karandash.contracts.telegram.TelegramInboundMessage;
import ru.karandash.contracts.telegram.TelegramReply;

import java.io.IOException;

/**
 * Внутренний API для приёмщика Telegram. Доступ — только с сервисным токеном ({@code /internal/**}).
 */
@RestController
@RequestMapping("/internal/telegram")
public class TelegramIntakeController {

    private final TelegramIntakeService intakeService;

    public TelegramIntakeController(TelegramIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @PostMapping(path = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    TelegramReply receive(
            @RequestPart("message") TelegramInboundMessage message,
            @RequestPart(name = "photo", required = false) MultipartFile photo
    ) throws IOException {
        IncomingPhoto incomingPhoto = photo == null || photo.isEmpty()
                ? null
                : new IncomingPhoto(photo.getBytes(), photo.getContentType());
        return intakeService.handle(message, incomingPhoto);
    }
}
