package ru.karandash.core.backup;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupReportRepository extends JpaRepository<BackupReportEntity, String> {
}
