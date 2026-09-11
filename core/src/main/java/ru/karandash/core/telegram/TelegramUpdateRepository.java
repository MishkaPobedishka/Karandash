package ru.karandash.core.telegram;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

interface TelegramUpdateRepository extends JpaRepository<TelegramUpdateEntity, Long> {

    /** Занимает апдейт; 0 — его уже приняли раньше. */
    @Transactional
    @Modifying
    @Query(value = "insert into telegram_update (update_id) values (:updateId) on conflict (update_id) do nothing",
            nativeQuery = true)
    int claim(@Param("updateId") long updateId);

    @Transactional
    @Modifying
    @Query(value = "delete from telegram_update where received_at < :before", nativeQuery = true)
    int deleteReceivedBefore(@Param("before") Instant before);
}
