package ru.karandash.core.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Отчёты ночной копии базы: их присылает CronJob, а ядро превращает их в метрики.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);

    private final BackupReportRepository reports;
    private final Clock clock;

    public BackupService(BackupReportRepository reports, Clock clock) {
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional
    public void record(BackupReport report) {
        BackupReportEntity entity = reports.findById(report.service())
                .orElseGet(() -> new BackupReportEntity(report.service()));
        entity.apply(report, clock.instant());
        reports.saveAndFlush(entity);
        if (report.ok()) {
            log.info("Копия {}: готова, {} МБ за {} с", report.service(),
                    report.sizeBytes() / 1_048_576, report.durationMs() / 1000);
        } else {
            log.warn("Копия {} не удалась: {}", report.service(), report.message());
        }
    }

    @Transactional(readOnly = true)
    public List<BackupReportEntity> all() {
        return reports.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<BackupReportEntity> last(String service) {
        return reports.findById(service);
    }
}
