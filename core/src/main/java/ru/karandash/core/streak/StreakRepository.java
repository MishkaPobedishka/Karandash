package ru.karandash.core.streak;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface StreakRepository extends JpaRepository<StreakEntity, UUID> {
}
