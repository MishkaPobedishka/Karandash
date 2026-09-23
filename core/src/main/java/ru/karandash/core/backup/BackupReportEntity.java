package ru.karandash.core.backup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Чем закончилась последняя резервная копия. Строка одна на вид копии: её переписывает каждый ночной запуск.
 */
@Entity
@Table(name = "backup_report")
public class BackupReportEntity {

    @Id
    private String name;

    @Column(nullable = false)
    private Instant finishedAt;

    /** Когда копия в последний раз получилась: неудачный запуск это время не сдвигает. */
    private Instant lastSuccessAt;

    @Column(nullable = false)
    private boolean ok;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private long durationMs;

    private String message;

    protected BackupReportEntity() {
    }

    BackupReportEntity(String name) {
        this.name = name;
        this.finishedAt = Instant.now();
        this.ok = false;
    }

    void apply(BackupReport report, Instant now) {
        this.finishedAt = now;
        this.ok = report.ok();
        this.sizeBytes = Math.max(0, report.sizeBytes());
        this.durationMs = Math.max(0, report.durationMs());
        this.message = report.message() == null ? null : report.message().strip();
        if (report.ok()) {
            this.lastSuccessAt = now;
        }
    }

    public String getName() {
        return name;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public boolean isOk() {
        return ok;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public String getMessage() {
        return message;
    }
}
