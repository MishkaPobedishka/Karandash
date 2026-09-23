package ru.karandash.core.canteen;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface LunchDialogRepository extends JpaRepository<LunchDialogEntity, UUID> {
}
