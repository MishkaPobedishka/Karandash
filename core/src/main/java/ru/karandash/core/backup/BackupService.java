package ru.karandash.core.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Отчёты ночной копии базы: их присылает CronJob, а ядро превращает их в метрики.
 */
@Service
public class BackupService {

    /** Пока копия одна — дамп PostgreSQL. Имя понадобится, если появятся другие копии. */
    public static final String POSTGRES = "postgres";

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupReportRepository reports;
    private final Clock clock;

    public BackupService(BackupReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional
    public void record(String name, BackupReport report) {
        BackupReportEntity entity = reports.findById(name).orElseGet(() -> new BackupReportEntity(name));
        entity.apply(report, clock.instant());
        reports.saveAndFlush(entity);
        if (report.ok()) {
            log.info("Копия базы {}: готова, {} МБ за {} с", name,
                    report.sizeBytes() / 1_048_576, report.durationMs() / 1000);
        } else {
            log.warn("Копия базы {} не удалась: {}", name, report.message());
        }
    }

    @Transactional(readOnly = true)
    public Optional<BackupReportEntity> last(String name) {
        return reports.findById(name);
    }
}
