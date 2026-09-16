package ru.karandash.core.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface TelegramIdentityRepository extends JpaRepository<TelegramIdentityEntity, Long> {

    Optional<TelegramIdentityEntity> findByAccountId(UUID accountId);

    @Modifying
    @Query(value = """
            insert into telegram_identity (telegram_id, account_id, display_name, username)
            values (:telegramId, :accountId, :displayName, :username)
            on conflict (telegram_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("telegramId") long telegramId,
            @Param("accountId") UUID accountId,
            @Param("displayName") String displayName,
            @Param("username") String username);
}
