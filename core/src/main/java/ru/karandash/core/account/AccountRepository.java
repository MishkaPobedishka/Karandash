package ru.karandash.core.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
}
