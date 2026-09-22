package ru.karandash.core.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsageRecordRepository extends JpaRepository<UsageRecordEntity, UUID> {

    List<UsageRecordEntity> findByAccountId(UUID accountId);

    /** Когда модель отвечала в последний раз: неудачные вызовы помечены суффиксом в виде вызова. */
    @Query(value = "select max(created_at) from usage_record where kind not like concat('%', :failureSuffix)",
            nativeQuery = true)
    Optional<Instant> findLastSuccessAt(@Param("failureSuffix") String failureSuffix);
}
