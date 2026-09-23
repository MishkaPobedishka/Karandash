package ru.karandash.core.backup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Внутренний эндпоинт для ночных копий: CronJob любого сервиса кластера сообщает, чем всё кончилось.
 * Как и остальные {@code /internal/*}, закрыт сервисным токеном.
 */
@RestController
@RequestMapping("/internal/backup")
class BackupController {

    private final BackupService backups;

    BackupController(BackupService backups) {
        this.backups = backups;
    }

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void report(@RequestBody BackupReport report) {
        backups.record(report);
    }
}
