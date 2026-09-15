package ru.karandash.core.diary;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface MealDraftRepository extends JpaRepository<MealDraftEntity, UUID> {

    Optional<MealDraftEntity> findByAccountId(UUID accountId);

    /** Черновик берётся вместе с аккаунтом: чужой идентификатор ничего не откроет. */
    Optional<MealDraftEntity> findByIdAndAccountId(UUID id, UUID accountId);

    @Transactional
    @Modifying
    int deleteByAccountId(UUID accountId);

    @Transactional
    @Modifying
    @Query(value = "delete from meal_draft where updated_at < :before", nativeQuery = true)
    int deleteUpdatedBefore(@Param("before") Instant before);
}
