package ru.karandash.core.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface GoalRepository extends JpaRepository<GoalEntity, UUID> {

    List<GoalEntity> findByAccountIdAndStatus(UUID accountId, String status);

    Optional<GoalEntity> findFirstByAccountIdAndStatusOrderByCreatedAtDesc(UUID accountId, String status);
}
