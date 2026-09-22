package ru.karandash.core.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID> {

    List<ReminderEntity> findByAccountId(UUID accountId);

    Optional<ReminderEntity> findByAccountIdAndType(UUID accountId, ReminderType type);
}
