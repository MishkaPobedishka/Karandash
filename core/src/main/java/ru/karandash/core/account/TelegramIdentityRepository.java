package ru.karandash.core.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface TelegramIdentityRepository extends JpaRepository<TelegramIdentityEntity, Long> {

    @Modifying
    @Query(value = """
            insert into telegram_identity (telegram_id, account_id)
            values (:telegramId, :accountId)
            on conflict (telegram_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("telegramId") long telegramId, @Param("accountId") UUID accountId);
}
