package ru.karandash.core.usage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, UUID> {

    List<UsageRecordEntity> findByAccountId(UUID accountId);
}
