package ru.karandash.core.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    /** Строка «кто написал»: аккаунт вместе с привязанным Telegram. */
    interface AccessRow {

        long getTelegramId();

        UUID getAccountId();

        String getDisplayName();

        String getUsername();

        String getRole();

        String getAccess();
    }

    String SELECT_ACCESS = """
            select ti.telegram_id as telegramId, a.id as accountId, ti.display_name as displayName,
                   ti.username as username, a.role as role, a.access as access
            from account a join telegram_identity ti on ti.account_id = a.id
            """;

    @Query(value = SELECT_ACCESS + " where ti.telegram_id = :telegramId", nativeQuery = true)
    Optional<AccessRow> findAccessByTelegramId(@Param("telegramId") long telegramId);

    @Query(value = SELECT_ACCESS + " where a.id = :accountId", nativeQuery = true)
    Optional<AccessRow> findAccessByAccountId(@Param("accountId") UUID accountId);

    @Query(value = SELECT_ACCESS + " where a.access = :access order by a.access_changed_at", nativeQuery = true)
    List<AccessRow> findAccessByState(@Param("access") String access);

    @Query(value = SELECT_ACCESS + " where a.role = 'ADMIN' and a.access = 'ALLOWED'", nativeQuery = true)
    List<AccessRow> findAdmins();

    @Query(value = SELECT_ACCESS + " where a.access = 'ALLOWED' order by a.created_at", nativeQuery = true)
    List<AccessRow> findAllowed();

    /**
     * Решение по заявке принимает тот администратор, чьё нажатие пришло первым:
     * условие на состояние делает второе нажатие безобидным.
     *
     * @return 1 — решение записано, 0 — заявку уже закрыли
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update account set access = :access, decided_by = :adminAccountId, access_changed_at = now()
            where id = :accountId and access = 'REQUESTED'
            """, nativeQuery = true)
    int decideRequest(
            @Param("accountId") UUID accountId,
            @Param("access") String access,
            @Param("adminAccountId") UUID adminAccountId);
}
